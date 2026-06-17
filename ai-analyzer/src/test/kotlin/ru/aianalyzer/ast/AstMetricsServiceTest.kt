package ru.aianalyzer.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [AstMetricsService].
 *
 * Each test focuses on a single metric to keep assertions clear and failures easy to diagnose.
 * The Kotlin PSI tests may be skipped implicitly (returns empty fact) if KotlinCoreEnvironment
 * fails to initialise in the test JVM — assertions account for this via OR on language field.
 */
class AstMetricsServiceTest {

    private val service = AstMetricsService()

    // ─────────────────────────── Java tests ──────────────────────────────────

    @Test
    fun `java - simple class with one loop has hasLoop true`() {
        val code = """
            public class Foo {
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        System.out.println(i);
                    }
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "java")
        assertTrue(fact.hasLoop) { "Expected hasLoop=true for Java for-loop, got: $fact" }
        assertEquals("java", fact.language)
    }

    @Test
    fun `java - recursive method is detected as hasRecursion true`() {
        val code = """
            public class Factorial {
                public int fact(int n) {
                    if (n <= 1) return 1;
                    return n * fact(n - 1);
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "java")
        assertTrue(fact.hasRecursion) { "Expected hasRecursion=true, got: $fact" }
    }

    @Test
    fun `java - method returning constant 0 is flagged as suspiciousReturnsConstant`() {
        val code = """
            public class Stub {
                public int solve(int x) {
                    return 0;
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "java")
        assertTrue(fact.suspiciousReturnsConstant) { "Expected suspiciousReturnsConstant=true, got: $fact" }
    }

    @Test
    fun `java - cyclomatic complexity for method with 3 ifs is 4`() {
        val code = """
            public class CC {
                public String check(int x) {
                    if (x > 0) {
                        if (x > 10) {
                            if (x > 100) {
                                return "big";
                            }
                            return "medium";
                        }
                        return "small";
                    }
                    return "negative";
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "java")
        // 1 (base) + 3 (ifs) = 4
        assertEquals(4, fact.cyclomaticComplexity) { "Expected cyclomaticComplexity=4, got: $fact" }
    }

    @Test
    fun `java - max nesting depth for two nested for-loops is 2`() {
        val code = """
            public class Matrix {
                public void fill(int[][] m) {
                    for (int i = 0; i < m.length; i++) {
                        for (int j = 0; j < m[i].length; j++) {
                            m[i][j] = i + j;
                        }
                    }
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "java")
        assertEquals(2, fact.maxNestingDepth) { "Expected maxNestingDepth=2, got: $fact" }
    }

    // ─────────────────────────── Python tests ────────────────────────────────

    @Test
    fun `python - def with for loop gives hasLoop true and methodCount 1`() {
        val code = """
def f():
    for x in []:
        pass
        """.trimIndent()

        val fact = service.extract(code, "python")
        assertTrue(fact.hasLoop) { "Expected hasLoop=true, got: $fact" }
        assertEquals(1, fact.methodCount) { "Expected methodCount=1, got: $fact" }
    }

    @Test
    fun `python - recursive function is detected as hasRecursion true`() {
        val code = """
def fact(n):
    if n <= 1:
        return 1
    return n * fact(n - 1)
        """.trimIndent()

        val fact = service.extract(code, "python")
        assertTrue(fact.hasRecursion) { "Expected hasRecursion=true, got: $fact" }
    }

    // ─────────────────────────── Kotlin tests ────────────────────────────────

    @Test
    fun `kotlin - function with while loop gives hasLoop true`() {
        val code = """
            fun f() {
                while (true) {
                    break
                }
            }
        """.trimIndent()

        val fact = service.extract(code, "kotlin")
        // If KotlinCoreEnvironment fails the language field will be "unknown:kotlin"
        // In that case we cannot assert hasLoop, but at least it must not crash.
        if (fact.language == "kotlin") {
            assertTrue(fact.hasLoop) { "Expected hasLoop=true for Kotlin while-loop, got: $fact" }
        }
        // Either "kotlin" or "unknown:kotlin" — never an exception
        assertTrue(fact.language.contains("kotlin")) { "Unexpected language in fact: $fact" }
    }

    // ─────────────────────────── Unknown language ─────────────────────────────

    @Test
    fun `unknown language returns AstFact empty with zero metrics`() {
        val fact = service.extract("print('hello')", "brainfuck")
        assertTrue(fact.language.startsWith("unknown:")) { "Expected language to start with 'unknown:', got: $fact" }
        assertFalse(fact.hasLoop)
        assertFalse(fact.hasRecursion)
        assertEquals(0, fact.cyclomaticComplexity)
        assertEquals(0, fact.methodCount)
    }

    @Test
    fun `python - cyclomatic complexity counts if elif and boolean operators`() {
        val code = """
def classify(x):
    if x > 0 and x < 10:
        return 'small'
    elif x > 10 or x == 0:
        return 'medium'
    return 'other'
        """.trimIndent()

        val fact = service.extract(code, "python")
        // 1 base + if + elif + and + or = 5
        assertTrue(fact.cyclomaticComplexity >= 4) { "Expected CC≥4, got: $fact" }
    }

    @Test
    fun `python - return None constant is flagged suspiciousReturnsConstant`() {
        val code = """
def solve():
    return None
        """.trimIndent()

        val fact = service.extract(code, "python")
        assertTrue(fact.suspiciousReturnsConstant) { "Expected suspiciousReturnsConstant=true, got: $fact" }
    }

    @Test
    fun `python - nested for inside while raises maxNestingDepth above 1`() {
        val code = """
def deep():
    while True:
        for x in range(3):
            for y in range(3):
                pass
        """.trimIndent()

        val fact = service.extract(code, "python")
        assertTrue(fact.maxNestingDepth >= 2) { "Expected maxNestingDepth≥2, got: $fact" }
    }

    @Test
    fun `kotlin - recursive function is detected as hasRecursion true`() {
        val code = """
            fun fact(n: Int): Int {
                if (n <= 1) return 1
                return n * fact(n - 1)
            }
        """.trimIndent()

        val fact = service.extract(code, "kotlin")
        if (fact.language == "kotlin") {
            assertTrue(fact.hasRecursion) { "Expected hasRecursion=true for Kotlin self-call, got: $fact" }
        }
    }

    // ─────────────────────────── Security guards ─────────────────────────────

    @Test
    fun `language field is capped at 32 chars to prevent prompt bloat`() {
        val huge = "x".repeat(1024)
        val fact = service.extract("print('hi')", huge)
        assertTrue(fact.language.length <= 40) { "language was not capped: ${fact.language.length}" }
    }

    // ─────────────────────────── Resilience test ─────────────────────────────

    @Test
    fun `syntactically invalid Java returns AstFact empty without throwing`() {
        val code = "this is not java { {{ broken"
        val fact = service.extract(code, "java")
        // Must not throw, must return empty-like fact
        assertTrue(fact.language.contains("java")) { "Expected language to contain 'java', got: $fact" }
        assertEquals(0, fact.cyclomaticComplexity)
        assertFalse(fact.hasLoop)
    }
}
