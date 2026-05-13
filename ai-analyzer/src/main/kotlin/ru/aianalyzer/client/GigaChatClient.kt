package ru.aianalyzer.client

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

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

open class GigaChatClient(
    private val webClient: WebClient,
    private val config: GigaChatClientConfig
) {

    private data class CachedToken(val value: String, val expiresAt: Instant)

    private val cachedToken = AtomicReference<CachedToken?>(null)

    open fun chatCompletion(systemPrompt: String, userPrompt: String): String {
        val token = obtainAccessToken()
        val request = GigaChatChatRequest(
            model = config.model,
            messages = listOf(
                GigaChatMessage(role = "system", content = systemPrompt),
                GigaChatMessage(role = "user", content = userPrompt)
            )
        )
        val response = webClient.post()
            .uri("${config.apiBaseUrl}/api/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus({ it.is5xxServerError }) { resp ->
                resp.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
                    Mono.error(GigaChatTransientException("GigaChat 5xx: ${resp.statusCode()} body=${body.take(500)}"))
                }
            }
            .onStatus({ it.value() == 401 }) { resp ->
                cachedToken.set(null)
                resp.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
                    Mono.error(GigaChatAuthException("GigaChat 401 unauthorized: ${body.take(300)}"))
                }
            }
            .bodyToMono(GigaChatChatResponse::class.java)
            .timeout(config.requestTimeout)
            .retryWhen(retrySpec("chatCompletion"))
            .block(config.requestTimeout.plusSeconds(5))
            ?: throw GigaChatException("GigaChat returned null response")

        val content = response.choices.firstOrNull()?.message?.content
            ?: throw GigaChatException("GigaChat response has no message content")
        logger.debug { "GigaChat finishReason=${response.choices.firstOrNull()?.finishReason} contentLen=${content.length}" }
        return content
    }

    private fun obtainAccessToken(): String {
        val now = Instant.now()
        cachedToken.get()?.let { cached ->
            if (cached.expiresAt.isAfter(now.plusSeconds(60))) {
                return cached.value
            }
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
        val response = webClient.post()
            .uri("${config.oauthBaseUrl}/api/v2/oauth")
            .header(HttpHeaders.AUTHORIZATION, "Basic ${config.authKey}")
            .header("RqUID", rqUid)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue("scope=${config.scope}")
            .retrieve()
            .onStatus(HttpStatusCode::is5xxServerError) { resp ->
                resp.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
                    Mono.error(GigaChatTransientException("OAuth 5xx: ${resp.statusCode()} body=${body.take(500)}"))
                }
            }
            .bodyToMono(GigaChatTokenResponse::class.java)
            .timeout(config.requestTimeout)
            .retryWhen(retrySpec("oauth"))
            .block(config.requestTimeout.plusSeconds(5))
            ?: throw GigaChatException("OAuth returned null response")

        val ttlMs = config.tokenTtl.toMillis()
        val expiresAt = response.expiresAt
            ?.let { Instant.ofEpochMilli(it) }
            ?: Instant.now().plusMillis(ttlMs)
        return CachedToken(value = response.accessToken, expiresAt = expiresAt)
    }

    private fun retrySpec(op: String): Retry =
        Retry.backoff(config.maxRetries, config.initialBackoff)
            .maxBackoff(Duration.ofSeconds(5))
            .filter { err -> isTransient(err) }
            .doBeforeRetry { signal ->
                logger.warn {
                    "Retry GigaChat op=$op attempt=${signal.totalRetries() + 1} cause=${signal.failure()::class.simpleName}: ${signal.failure().message}"
                }
            }
            .onRetryExhaustedThrow { _, signal ->
                GigaChatException("GigaChat $op retries exhausted (${signal.totalRetries()})", signal.failure())
            }

    private fun isTransient(err: Throwable): Boolean = when (err) {
        is GigaChatTransientException -> true
        is java.util.concurrent.TimeoutException -> true
        is IOException -> true
        is WebClientResponseException -> err.statusCode.is5xxServerError
        else -> err.cause?.let { isTransient(it) } ?: false
    }
}

open class GigaChatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class GigaChatTransientException(message: String, cause: Throwable? = null) : GigaChatException(message, cause)
class GigaChatAuthException(message: String, cause: Throwable? = null) : GigaChatException(message, cause)
