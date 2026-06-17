package ru.aianalyzer.ast

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Deterministic structural facts extracted from user-submitted code via AST/regex analysis.
 *
 * These facts are injected verbatim into the LLM prompt so the model can reference
 * objective code structure without hallucinating structural properties.
 *
 * @property language Language tag (e.g. "java", "python", "kotlin", "unknown:<original>").
 * @property hasLoop Whether the code contains any loop construct.
 * @property hasRecursion Whether any function/method calls itself.
 * @property hasComparison Whether any comparison operator is used.
 * @property cyclomaticComplexity McCabe cyclomatic complexity estimate.
 * @property methodCount Number of top-level function/method declarations.
 * @property maxNestingDepth Maximum block nesting depth detected.
 * @property suspiciousReturnsConstant Whether the code looks like a stub that just returns a constant.
 * @property lineCount Total source line count (including blank lines).
 */
public data class AstFact(
    val language: String,
    val hasLoop: Boolean,
    val hasRecursion: Boolean,
    val hasComparison: Boolean,
    val cyclomaticComplexity: Int,
    val methodCount: Int,
    val maxNestingDepth: Int,
    val suspiciousReturnsConstant: Boolean,
    val lineCount: Int,
) {
    /**
     * Returns a compact JSON string suitable for embedding in an LLM prompt.
     */
    public fun toPromptJson(): String = MAPPER.writeValueAsString(this)

    public companion object {
        private val MAPPER = jacksonObjectMapper()

        /**
         * Returns a zero-value [AstFact] used when parsing fails or the language is unsupported.
         *
         * The [language] field is preserved so the LLM prompt can indicate that AST analysis
         * was not available for the given language.
         */
        public fun empty(language: String): AstFact = AstFact(
            language = "unknown:$language",
            hasLoop = false,
            hasRecursion = false,
            hasComparison = false,
            cyclomaticComplexity = 0,
            methodCount = 0,
            maxNestingDepth = 0,
            suspiciousReturnsConstant = false,
            lineCount = 0,
        )
    }
}

/**
 * Formats an [AstFact] into a fenced XML-like block for clean embedding inside a system prompt.
 */
public fun AstFact.spotlightForPrompt(): String =
    "<AST_FACTS>\n${toPromptJson()}\n</AST_FACTS>"
