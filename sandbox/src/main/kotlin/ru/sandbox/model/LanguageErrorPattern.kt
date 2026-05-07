package ru.sandbox.model

/**
 * Per-language stderr error detection patterns.
 *
 * These patterns are applied to the container's stderr output to detect
 * runtime errors even when the exit code is 0 (e.g., JVM printing stack
 * traces to stderr before the main thread exits cleanly).
 *
 * Rules:
 * - Java: match "Exception in thread" or any "java.lang.*Error"
 * - Python: match "Traceback (most recent call last)"
 *
 * Kotlin support is intentionally omitted per project scope.
 * Patterns are anchored with MULTILINE so they match anywhere in stderr.
 */
sealed class LanguageErrorPattern(val language: String, val pattern: Regex) {

    object Java : LanguageErrorPattern(
        language = "java",
        pattern = Regex(
            pattern = "Exception in thread|java\\.lang\\.\\w+Error",
            options = setOf(RegexOption.MULTILINE)
        )
    )

    object Python : LanguageErrorPattern(
        language = "python",
        pattern = Regex(
            pattern = "Traceback \\(most recent call last\\)",
            options = setOf(RegexOption.MULTILINE)
        )
    )

    companion object {
        /**
         * Returns the matching pattern for the given language name,
         * or null if the language is not supported / has no defined pattern.
         */
        fun forLanguage(language: String): LanguageErrorPattern? = when (language.lowercase()) {
            "java" -> Java
            "python" -> Python
            else -> null
        }

        /**
         * Returns true if [stderr] contains a known fatal error marker
         * for the given language.
         */
        fun stderrIndicatesError(language: String, stderr: String): Boolean {
            val pat = forLanguage(language) ?: return false
            return pat.pattern.containsMatchIn(stderr)
        }
    }
}
