package ru.aianalyzer.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import ru.aianalyzer.ast.AstMetricsService
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatClientConfig
import ru.aianalyzer.metrics.AiAnalyzerMetrics
import ru.aianalyzer.prompt.PromptVariant
import ru.aianalyzer.service.AIAnalyzer
import ru.aianalyzer.service.AIAnalysisResult
import ru.aianalyzer.service.AstHybridAnalyzer
import ru.aianalyzer.service.GigaChatAnalyzer
import ru.aianalyzer.service.SimpleRuleBasedAnalyzer
import ru.aianalyzer.validation.SchemaValidator
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class AiAnalyzerConfig {

    @Bean(name = ["aiAnalyzerObjectMapper"])
    fun aiAnalyzerObjectMapper(): ObjectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    // GigaChat HTTP client is now JDK-based; no Spring WebClient bean is needed.

    @Bean
    fun gigaChatClientConfig(
        @Value("\${gigachat.auth-key:}") authKey: String,
        @Value("\${gigachat.scope:GIGACHAT_API_PERS}") scope: String,
        @Value("\${gigachat.model:GigaChat}") model: String,
        @Value("\${gigachat.oauth-base-url:https://ngw.devices.sberbank.ru:9443}") oauthBaseUrl: String,
        @Value("\${gigachat.api-base-url:https://gigachat.devices.sberbank.ru}") apiBaseUrl: String,
        @Value("\${gigachat.request-timeout-seconds:30}") requestTimeoutSeconds: Long,
        @Value("\${gigachat.max-retries:3}") maxRetries: Long,
        @Value("\${gigachat.token-ttl-minutes:30}") tokenTtlMinutes: Long,
        @Value("\${gigachat.tls.trust-all:true}") trustAll: Boolean
    ): GigaChatClientConfig = GigaChatClientConfig(
        authKey = authKey,
        scope = scope,
        model = model,
        oauthBaseUrl = oauthBaseUrl,
        apiBaseUrl = apiBaseUrl,
        requestTimeout = Duration.ofSeconds(requestTimeoutSeconds),
        maxRetries = maxRetries,
        tokenTtl = Duration.ofMinutes(tokenTtlMinutes),
        trustAll = trustAll
    )

    @Bean
    fun gigaChatClient(config: GigaChatClientConfig): GigaChatClient = GigaChatClient(config)

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
    fun schemaValidator(
        @org.springframework.beans.factory.annotation.Qualifier("aiAnalyzerObjectMapper")
        objectMapper: ObjectMapper
    ): SchemaValidator = SchemaValidator(objectMapper)

    @Bean(name = ["gigaChatAnalyzer"])
    fun gigaChatAnalyzer(
        client: GigaChatClient,
        @org.springframework.beans.factory.annotation.Qualifier("aiAnalyzerObjectMapper")
        objectMapper: ObjectMapper,
        fallback: SimpleRuleBasedAnalyzer,
        @org.springframework.beans.factory.annotation.Qualifier("aiAnalysisCache")
        cache: Cache<String, AIAnalysisResult>,
        schemaValidator: SchemaValidator,
        astMetricsService: AstMetricsService,
        // Nullable: in slice-tests like ProviderSwitchTest the @Component-scanned
        // AiAnalyzerMetrics bean isn't on the context. Production AiAnalyzerConfig
        // is loaded alongside the metrics @Component via @SpringBootApplication
        // scan, so this is non-null at runtime.
        aiAnalyzerMetrics: AiAnalyzerMetrics?,
        @Value("\${ai.prompt.variant:zero-shot}") promptVariantProp: String,
        // Step 6a / B.6: feature-flag для explainError / assessCodeQuality через LLM.
        // По умолчанию false — оба метода идут в rule-based fallback (как и до правки).
        // analyze()-pipeline этим флагом не затрагивается.
        @Value("\${ai.explain-via-llm:false}") explainViaLlm: Boolean
    ): GigaChatAnalyzer {
        val variant = when (promptVariantProp.lowercase().trim()) {
            "few-shot", "few_shot", "fewshot" -> PromptVariant.FEW_SHOT
            else -> PromptVariant.ZERO_SHOT
        }
        // P0-3: подключаем AST-extractor по умолчанию, чтобы в любом production-пути
        // (через AstHybridAnalyzer или прямой GigaChatAnalyzer) prompt содержал
        // блок <AST_FACTS> с детерминированными структурными фактами кода.
        return GigaChatAnalyzer(
            client = client,
            objectMapper = objectMapper,
            fallback = fallback,
            cache = cache,
            schemaValidator = schemaValidator,
            promptVariant = variant,
            astMetricsService = astMetricsService,
            metrics = aiAnalyzerMetrics,
            explainViaLlm = explainViaLlm,
        )
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = ["ai.analyzer.provider"], havingValue = "gigachat", matchIfMissing = true)
    fun primaryGigaChat(gigaChatAnalyzer: GigaChatAnalyzer): AIAnalyzer = gigaChatAnalyzer

    @Bean
    @Primary
    @ConditionalOnProperty(name = ["ai.analyzer.provider"], havingValue = "rule-based")
    fun primaryRuleBased(ruleBasedAnalyzer: SimpleRuleBasedAnalyzer): AIAnalyzer = ruleBasedAnalyzer

    @Bean
    fun astMetricsService(): AstMetricsService = AstMetricsService()

    @Bean(name = ["astHybridAnalyzer"])
    fun astHybridAnalyzer(
        gigaChatAnalyzer: GigaChatAnalyzer,
        astMetricsService: AstMetricsService,
        fallback: SimpleRuleBasedAnalyzer,
        aiAnalyzerMetrics: AiAnalyzerMetrics?
    ): AstHybridAnalyzer = AstHybridAnalyzer(gigaChatAnalyzer, astMetricsService, fallback, aiAnalyzerMetrics)

    @Bean
    @Primary
    @ConditionalOnProperty(name = ["ai.analyzer.provider"], havingValue = "ast-hybrid")
    fun primaryAstHybrid(astHybridAnalyzer: AstHybridAnalyzer): AIAnalyzer = astHybridAnalyzer
}
