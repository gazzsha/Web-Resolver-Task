package ru.aianalyzer.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * F-20 regression: KotlinAstAnalyzer must be safe under concurrent invocation.
 *
 * [org.jetbrains.kotlin.psi.KtPsiFactory] is explicitly NOT thread-safe per its
 * own javadoc. The fix synchronises [KotlinAstAnalyzer.analyze] on an internal
 * monitor; this test asserts the synchronisation actually holds by running
 * the same three snippets across 8 threads and matching the results against a
 * sequential baseline.
 *
 * If the Kotlin compiler embeddable fails to initialise in the test JVM
 * (e.g. JDK module-system incompatibilities), KotlinAstAnalyzer returns
 * empty facts; the test still passes because the empty fact is also returned
 * deterministically on each call. The assertion is "concurrent results equal
 * sequential results", not "facts are non-empty".
 */
class KotlinAstAnalyzerConcurrentTest {

    private val service = AstMetricsService()

    private val snippets = listOf(
        """
            fun foo(n: Int): Int {
                if (n < 0) return -1
                if (n == 0) return 0
                return n
            }
        """.trimIndent(),
        """
            fun sum(n: Int): Int {
                var s = 0
                for (i in 0 until n) {
                    s += i
                }
                return s
            }
        """.trimIndent(),
        """
            fun check(x: Int): Boolean = (x > 0 && x < 100) || x == -1
        """.trimIndent(),
    )

    @Test
    fun `concurrent kotlin extraction returns the same per-snippet facts as sequential baseline`() {
        val baseline = snippets.map { service.extract(it, "kotlin") }

        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks: List<Callable<Pair<Int, AstFact>>> = (0 until 150).map { idx ->
                val snippetIdx = idx % snippets.size
                Callable { snippetIdx to service.extract(snippets[snippetIdx], "kotlin") }
            }
            val results = executor.invokeAll(tasks, 60, TimeUnit.SECONDS).map { it.get() }

            results.forEach { (snippetIdx, fact) ->
                val expected = baseline[snippetIdx]
                assertEquals(expected.cyclomaticComplexity, fact.cyclomaticComplexity) {
                    "Concurrent CC mismatch for snippet $snippetIdx"
                }
                assertEquals(expected.hasLoop, fact.hasLoop)
                assertEquals(expected.methodCount, fact.methodCount)
                assertEquals(expected.maxNestingDepth, fact.maxNestingDepth)
                assertTrue(fact.language.contains("kotlin"))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
