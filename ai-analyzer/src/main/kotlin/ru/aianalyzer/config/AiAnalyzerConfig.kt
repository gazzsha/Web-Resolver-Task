package ru.aianalyzer.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatClientConfig
import ru.aianalyzer.service.AIAnalyzer
import ru.aianalyzer.service.AIAnalysisResult
import ru.aianalyzer.service.GigaChatAnalyzer
import ru.aianalyzer.service.SimpleRuleBasedAnalyzer
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class AiAnalyzerConfig {

    @Bean(name = ["aiAnalyzerObjectMapper"])
    fun aiAnalyzerObjectMapper(): ObjectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Bean(name = ["gigaChatWebClient"])
    fun gigaChatWebClient(
        @Value("\${gigachat.connect-timeout-ms:5000}") connectTimeoutMs: Int,
        @Value("\${gigachat.response-timeout-ms:30000}") responseTimeoutMs: Long
    ): WebClient {
        // dev-only: trust-all SSL для self-signed CA Сбера; в проде заменить на bundle с минцифровским CA.
        val sslContext = SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
        val httpClient = HttpClient.create()
            .secure { it.sslContext(sslContext) }
            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(1 * 1024 * 1024) }
            .build()
    }

    @Bean
    fun gigaChatClientConfig(
        @Value("\${gigachat.auth-key:}") authKey: String,
        @Value("\${gigachat.scope:GIGACHAT_API_PERS}") scope: String,
        @Value("\${gigachat.model:GigaChat}") model: String,
        @Value("\${gigachat.oauth-base-url:https://ngw.devices.sberbank.ru:9443}") oauthBaseUrl: String,
        @Value("\${gigachat.api-base-url:https://gigachat.devices.sberbank.ru}") apiBaseUrl: String,
        @Value("\${gigachat.request-timeout-seconds:30}") requestTimeoutSeconds: Long,
        @Value("\${gigachat.max-retries:3}") maxRetries: Long,
        @Value("\${gigachat.token-ttl-minutes:30}") tokenTtlMinutes: Long
    ): GigaChatClientConfig = GigaChatClientConfig(
        authKey = authKey,
        scope = scope,
        model = model,
        oauthBaseUrl = oauthBaseUrl,
        apiBaseUrl = apiBaseUrl,
        requestTimeout = Duration.ofSeconds(requestTimeoutSeconds),
        maxRetries = maxRetries,
        tokenTtl = Duration.ofMinutes(tokenTtlMinutes)
    )

    @Bean
    fun gigaChatClient(
        @org.springframework.beans.factory.annotation.Qualifier("gigaChatWebClient")
        webClient: WebClient,
        config: GigaChatClientConfig
    ): GigaChatClient = GigaChatClient(webClient, config)

    @Bean(name = ["aiAnalysisCache"])
    fun aiAnalysisCache(
        @Value("\${gigachat.cache.ttl-minutes:60}") ttlMinutes: Long,
        @Value("\${gigachat.cache.max-size:500}") maxSize: Long
    ): Cache<String, AIAnalysisResult> = Caffeine.newBuilder()
        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
        .maximumSize(maxSize)
        .build()

    @Bean
    fun ruleBasedAnalyzer(): SimpleRuleBasedAnalyzer = SimpleRuleBasedAnalyzer()

    @Bean
    @Primary
    fun gigaChatAnalyzer(
        client: GigaChatClient,
        @org.springframework.beans.factory.annotation.Qualifier("aiAnalyzerObjectMapper")
        objectMapper: ObjectMapper,
        fallback: SimpleRuleBasedAnalyzer,
        @org.springframework.beans.factory.annotation.Qualifier("aiAnalysisCache")
        cache: Cache<String, AIAnalysisResult>
    ): AIAnalyzer = GigaChatAnalyzer(client, objectMapper, fallback, cache)
}
