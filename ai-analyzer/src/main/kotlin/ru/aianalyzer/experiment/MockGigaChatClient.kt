package ru.aianalyzer.experiment

import com.fasterxml.jackson.databind.ObjectMapper
import ru.aianalyzer.client.GigaChatClient
import ru.aianalyzer.client.GigaChatClientConfig
import java.time.Duration

/**
 * In-process GigaChat stub for the experiment runner. Returns canned JSON responses keyed by
 * the [DiagnosisHint] threaded through a thread-local set by [Runner] before each analyze() call.
 *
 * For attacks we randomly pick between ATTACK_NEUTRAL (defended) and ATTACK_SUCCUMBED (LLM gave in)
 * so the downstream V4 clamp / schema-reject path gets exercised. The selection is deterministic per
 * itemId for reproducibility.
 *
 * **EXPERIMENT-ONLY.** This class lives in src/main solely because the JavaExec runExperiment task
 * needs it on the runtime classpath. It is NOT a Spring `@Component` / `@Bean` and is never wired
 * into the production analyzer chain. The production AiAnalyzerConfig.gigaChatAnalyzer bean always
 * constructs a real [ru.aianalyzer.client.GigaChatClient]. Manual misuse (e.g. someone explicitly
 * registering this as @Primary) would still trip differential-review checks before deploy.
 */
class MockGigaChatClient(
    private val mockResponses: Map<String, String>,
    private val mapper: ObjectMapper,
    config: GigaChatClientConfig
) : GigaChatClient(config) {

    override fun chatCompletion(systemPrompt: String, userPrompt: String): String {
        val hint = MockContext.currentHint.get() ?: "WA"
        // Attacks: half pretend the LLM succumbed (codeQuality=95) to exercise the V4 clamp path.
        val key = when {
            hint.startsWith("ATTACK_") -> {
                val id = MockContext.currentItemId.get() ?: "0"
                if (id.hashCode() % 2 == 0) "ATTACK_NEUTRAL" else "ATTACK_SUCCUMBED"
            }
            hint in mockResponses -> hint
            else -> "WA"
        }
        return mockResponses[key] ?: mockResponses.getValue("WA")
    }

    companion object {
        fun load(mapper: ObjectMapper): Map<String, String> {
            val stream = MockGigaChatClient::class.java
                .getResourceAsStream("/experiment/mock-responses.json")
                ?: error("mock-responses.json not found on classpath")
            val root = stream.use { mapper.readTree(it) }
            val out = mutableMapOf<String, String>()
            val it = root.fields()
            while (it.hasNext()) {
                val (key, value) = it.next()
                out[key] = mapper.writeValueAsString(value)
            }
            return out
        }

        fun dummyConfig(): GigaChatClientConfig =
            GigaChatClientConfig(
                authKey = "mock",
                scope = "MOCK",
                model = "mock",
                oauthBaseUrl = "http://localhost",
                apiBaseUrl = "http://localhost",
                requestTimeout = Duration.ofSeconds(1),
                maxRetries = 0,
                tokenTtl = Duration.ofMinutes(1)
            )
    }
}

/** Thread-local hint plumbed from Runner.analyze() to the mock client. */
object MockContext {
    val currentHint: ThreadLocal<String?> = ThreadLocal.withInitial { null }
    val currentItemId: ThreadLocal<String?> = ThreadLocal.withInitial { null }

    inline fun <T> with(hint: String, itemId: String, block: () -> T): T {
        currentHint.set(hint)
        currentItemId.set(itemId)
        try {
            return block()
        } finally {
            currentHint.remove()
            currentItemId.remove()
        }
    }
}
