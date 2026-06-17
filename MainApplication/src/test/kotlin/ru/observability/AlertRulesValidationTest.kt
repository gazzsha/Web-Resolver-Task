package ru.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Paths

/**
 * Validates that the Prometheus alerting rules file passes the `promtool check rules` linter.
 *
 * The test is skipped by default because `promtool` is invoked via Docker, which requires
 * the Docker daemon to be running and adds ~10 s overhead per run.
 *
 * Enable by setting the environment variable:
 *   RUN_PROMTOOL_TESTS=true ./gradlew :MainApplication:test --tests "ru.observability.AlertRulesValidationTest"
 *
 * The rules file must exist at:
 *   <repo-root>/monitoring/rules/web-resolver.rules.yml
 *
 * If the file does not exist the test fails with an informative message so it is obvious
 * that the rules file must be created before enabling this gate in CI.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_PROMTOOL_TESTS",
    matches = "true",
    disabledReason = "Set RUN_PROMTOOL_TESTS=true to run promtool validation (requires Docker).",
)
class AlertRulesValidationTest {

    companion object {
        /** Absolute path to the repository root, resolved relative to this class's location. */
        private val REPO_ROOT: String by lazy {
            // The working directory during Gradle test execution is the module root
            // (MainApplication/). Walk up one level to reach the repo root.
            Paths.get(System.getProperty("user.dir"))
                .parent
                .toAbsolutePath()
                .toString()
        }

        private const val RULES_RELATIVE = "monitoring/rules/web-resolver.rules.yml"
        private const val PROMETHEUS_IMAGE = "prom/prometheus:v2.55.0"
        private const val CONTAINER_RULES_DIR = "/r"
    }

    @Test
    fun `web-resolver rules pass promtool check rules`() {
        val rulesFile = Paths.get(REPO_ROOT, RULES_RELATIVE).toFile()

        assertThat(rulesFile)
            .`as`(
                "Rules file must exist at \$REPO_ROOT/$RULES_RELATIVE. " +
                    "Create it before enabling RUN_PROMTOOL_TESTS.",
            )
            .exists()
            .isFile

        val rulesHostDir = rulesFile.parentFile.absolutePath

        // docker run --rm -v <host-rules-dir>:/r prom/prometheus:v2.55.0 promtool check rules /r/web-resolver.rules.yml
        val command = listOf(
            "docker", "run", "--rm",
            "-v", "$rulesHostDir:$CONTAINER_RULES_DIR",
            PROMETHEUS_IMAGE,
            "promtool", "check", "rules",
            "$CONTAINER_RULES_DIR/${rulesFile.name}",
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true) // merge stderr into stdout for a single capture
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertThat(exitCode)
            .`as`(
                "promtool check rules exited with non-zero code $exitCode.\n" +
                    "Command: ${command.joinToString(" ")}\n" +
                    "Output:\n$output",
            )
            .isEqualTo(0)

        assertThat(output)
            .`as`("promtool output must contain SUCCESS but was:\n$output")
            .contains("SUCCESS")
    }
}
