package ru.aianalyzer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import ru.aianalyzer.service.AIAnalyzer
import ru.aianalyzer.service.GigaChatAnalyzer
import ru.aianalyzer.service.SimpleRuleBasedAnalyzer

class ProviderSwitchTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of())
        .withUserConfiguration(AiAnalyzerConfig::class.java)

    @Test
    fun `default provider is gigachat (matchIfMissing)`() {
        runner.run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx.getBean(AIAnalyzer::class.java))
                .isInstanceOf(GigaChatAnalyzer::class.java)
        }
    }

    @Test
    fun `provider gigachat selects GigaChatAnalyzer`() {
        runner.withPropertyValues("ai.analyzer.provider=gigachat").run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx.getBean(AIAnalyzer::class.java))
                .isInstanceOf(GigaChatAnalyzer::class.java)
        }
    }

    @Test
    fun `provider rule-based selects SimpleRuleBasedAnalyzer`() {
        runner.withPropertyValues("ai.analyzer.provider=rule-based").run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx.getBean(AIAnalyzer::class.java))
                .isInstanceOf(SimpleRuleBasedAnalyzer::class.java)
        }
    }
}
