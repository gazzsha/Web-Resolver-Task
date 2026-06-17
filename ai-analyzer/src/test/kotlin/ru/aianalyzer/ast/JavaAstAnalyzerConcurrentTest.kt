package ru.aianalyzer.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * F-19 regression: JavaAstAnalyzer must be safe under concurrent invocation.
 *
 * Before the fix, [JavaAstAnalyzer] used the JVM-wide [com.github.javaparser.StaticJavaParser]
 * which the JavaParser project documents as not thread-safe. Two concurrent
 * worker threads parsing distinct snippets could observe each other's
 * intermediate parser state, producing wrong AST facts.
 *
 * This test runs 50× concurrent extractions of three distinct snippets (each
 * with a known cyclomatic complexity) and asserts every per-snippet result
 * matches the deterministic single-threaded baseline.
 */
class JavaAstAnalyzerConcurrentTest {

    private val service = AstMetricsService()

    private data class Snippet(val code: String, val expectedCc: Int, val expectedHasLoop: Boolean)

    private val snippets = listOf(
        Snippet(
            code = """
                public class A {
                    public int f(int n) {
                        if (n < 0) return -1;
                        if (n == 0) return 0;
                        return n;
                    }
                }
            """.trimIndent(),
            expectedCc = 3,
            expectedHasLoop = false,
        ),
        Snippet(
            code = """
                public class B {
                    public int sum(int n) {
                        int s = 0;
                        for (int i = 0; i < n; i++) {
                            s += i;
                        }
                        return s;
                    }
                }
            """.trimIndent(),
            expectedCc = 2,
            expectedHasLoop = true,
        ),
        Snippet(
            code = """
                public class C {
                    public boolean check(int x) {
                        return (x > 0 && x < 100) || x == -1;
                    }
                }
            """.trimIndent(),
            expectedCc = 3,
            expectedHasLoop = false,
        ),
    )

    @Test
    fun `concurrent extraction returns the same per-snippet facts as sequential baseline`() {
        // Step 1: sequential baseline. Captures the "ground truth" facts that
        // every concurrent run must match.
        val baseline = snippets.map { service.extract(it.code, "java") }
        baseline.forEachIndexed { i, fact ->
            assertEquals(snippets[i].expectedCc, fact.cyclomaticComplexity) {
                "Sequential baseline mismatch for snippet $i — test setup is broken"
            }
            assertEquals(snippets[i].expectedHasLoop, fact.hasLoop)
        }

        // Step 2: 50 concurrent invocations across 8 threads.
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks: List<Callable<Pair<Int, AstFact>>> = (0 until 150).map { idx ->
                val snippetIdx = idx % snippets.size
                Callable { snippetIdx to service.extract(snippets[snippetIdx].code, "java") }
            }
            val results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS)
                .map { it.get() }

            // Step 3: every result must equal its sequential baseline.
            results.forEach { (snippetIdx, fact) ->
                val expected = baseline[snippetIdx]
                assertEquals(expected.cyclomaticComplexity, fact.cyclomaticComplexity) {
                    "Concurrent CC mismatch for snippet $snippetIdx"
                }
                assertEquals(expected.hasLoop, fact.hasLoop)
                assertEquals(expected.methodCount, fact.methodCount)
                assertEquals(expected.maxNestingDepth, fact.maxNestingDepth)
                assertNotNull(fact.language)
                assertTrue(fact.language.contains("java"))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
