package ru.aianalyzer.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private val logger = KotlinLogging.logger {}

data class GigaChatClientConfig(
    val authKey: String,
    val scope: String = "GIGACHAT_API_PERS",
    val model: String = "GigaChat",
    val oauthBaseUrl: String = "https://ngw.devices.sberbank.ru:9443",
    val apiBaseUrl: String = "https://gigachat.devices.sberbank.ru",
    val requestTimeout: Duration = Duration.ofSeconds(30),
    val maxRetries: Long = 3,
    val initialBackoff: Duration = Duration.ofMillis(500),
    val tokenTtl: Duration = Duration.ofMinutes(30)
)

/**
 * GigaChat client built on JDK `java.net.http.HttpClient`.
 *
 * Why not Spring `WebClient` / reactor-netty? In live testing against Сбер
 * `/api/v1/chat/completions` returned 404 for reactor-netty over both HTTP/1.1
 * and HTTP/2 (Http2SslContextSpec with ALPN), while the same request via `curl`
 * succeeded on both protocols. JDK HttpClient works out of the box with a
 * minimal trust-all SSLContext and bypasses whatever reactor-netty was doing
 * differently (likely Transfer-Encoding: chunked on POSTs that the Sber edge
 * proxy rejects).
 */
open class GigaChatClient(
    private val config: GigaChatClientConfig,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {

    private data class CachedToken(val value: String, val expiresAt: Instant)

    private val cachedToken = AtomicReference<CachedToken?>(null)

    private val httpClient: JdkHttpClient = run {
        // dev-only trust-all SSL for Sber self-signed CA. Production must replace
        // this with a trust store bundled with the Минцифры root.
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tm, SecureRandom())
        JdkHttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    open fun chatCompletion(systemPrompt: String, userPrompt: String): String {
        val token = obtainAccessToken()
        val request = GigaChatChatRequest(
            model = config.model,
            messages = listOf(
                GigaChatMessage(role = "system", content = systemPrompt),
                GigaChatMessage(role = "user", content = userPrompt)
            )
        )
        val body = objectMapper.writeValueAsString(request)
        logger.info { "GigaChat chat URI=${config.apiBaseUrl}/api/v1/chat/completions body=$body tokenLen=${token.length}" }
        val httpReq = HttpRequest.newBuilder()
            .uri(URI.create("${config.apiBaseUrl}/api/v1/chat/completions"))
            .timeout(config.requestTimeout)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val resp = sendWithRetry(httpReq, "chatCompletion")
        when {
            resp.statusCode() == 401 -> {
                cachedToken.set(null)
                throw GigaChatAuthException("GigaChat 401 unauthorized: ${resp.body().take(300)}")
            }
            resp.statusCode() != 200 ->
                throw GigaChatException("GigaChat chat HTTP ${resp.statusCode()}: ${resp.body().take(500)}")
        }
        val parsed = objectMapper.readValue(resp.body(), GigaChatChatResponse::class.java)
        val content = parsed.choices.firstOrNull()?.message?.content
            ?: throw GigaChatException("GigaChat response has no message content")
        logger.debug { "GigaChat finishReason=${parsed.choices.firstOrNull()?.finishReason} contentLen=${content.length}" }
        return content
    }

    private fun obtainAccessToken(): String {
        val now = Instant.now()
        cachedToken.get()?.let { cached ->
            if (cached.expiresAt.isAfter(now.plusSeconds(60))) return cached.value
        }
        synchronized(this) {
            val current = cachedToken.get()
            if (current != null && current.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
                return current.value
            }
            val fresh = requestNewToken()
            cachedToken.set(fresh)
            return fresh.value
        }
    }

    private fun requestNewToken(): CachedToken {
        val rqUid = UUID.randomUUID().toString()
        logger.info { "Requesting new GigaChat OAuth token, rqUID=$rqUid scope=${config.scope}" }
        val formBody = "scope=" + URLEncoder.encode(config.scope, StandardCharsets.UTF_8)
        val httpReq = HttpRequest.newBuilder()
            .uri(URI.create("${config.oauthBaseUrl}/api/v2/oauth"))
            .timeout(config.requestTimeout)
            .header("Authorization", "Basic ${config.authKey}")
            .header("RqUID", rqUid)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
            .build()

        val resp = sendWithRetry(httpReq, "oauth")
        if (resp.statusCode() != 200) {
            throw GigaChatException("OAuth HTTP ${resp.statusCode()}: ${resp.body().take(500)}")
        }
        val parsed = objectMapper.readValue(resp.body(), GigaChatTokenResponse::class.java)
        val expiresAt = parsed.expiresAt?.let { Instant.ofEpochMilli(it) }
            ?: Instant.now().plus(config.tokenTtl)
        return CachedToken(value = parsed.accessToken, expiresAt = expiresAt)
    }

    /**
     * Synchronous retry loop with exponential backoff. Retries on:
     *   - IOException (network)
     *   - HttpTimeoutException (slow Sber)
     *   - HTTP 5xx
     * Non-transient errors (4xx) are returned as-is so the caller can map them.
     */
    private fun sendWithRetry(req: HttpRequest, op: String): HttpResponse<String> {
        var attempt = 0
        var backoff = config.initialBackoff
        while (true) {
            val r = runCatching { httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)) }
            r.fold(
                onSuccess = { resp ->
                    if (resp.statusCode() < 500) return resp
                    if (attempt >= config.maxRetries) {
                        throw GigaChatException("$op retries exhausted (status=${resp.statusCode()})")
                    }
                    logger.warn { "Retry $op attempt=${attempt + 1} status=${resp.statusCode()}" }
                },
                onFailure = { err ->
                    val transient = err is IOException || err is HttpTimeoutException
                    if (!transient || attempt >= config.maxRetries) {
                        throw GigaChatException("$op failed: ${err.message}", err)
                    }
                    logger.warn { "Retry $op attempt=${attempt + 1} cause=${err::class.simpleName}: ${err.message}" }
                }
            )
            Thread.sleep(backoff.toMillis())
            backoff = backoff.multipliedBy(2).coerceAtMost(Duration.ofSeconds(5))
            attempt++
        }
    }
}

open class GigaChatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class GigaChatTransientException(message: String, cause: Throwable? = null) : GigaChatException(message, cause)
class GigaChatAuthException(message: String, cause: Throwable? = null) : GigaChatException(message, cause)
