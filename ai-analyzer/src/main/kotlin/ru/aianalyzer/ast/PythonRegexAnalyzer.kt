package ru.aianalyzer.ast

/**
 * Extracts [AstFact] metrics from Python source code using regex and indent-based heuristics.
 *
 * No Python interpreter is required. All analysis is purely textual. Accuracy is best-effort;
 * complex metaprogramming or unusual style may produce false negatives.
 */
internal class PythonRegexAnalyzer {

    fun analyze(code: String): AstFact = AstFact(
        language = "python",
        hasLoop = detectLoop(code),
        hasRecursion = detectRecursion(code),
        hasComparison = detectComparison(code),
        cyclomaticComplexity = computeCyclomatic(code),
        methodCount = countMethods(code),
        maxNestingDepth = computeMaxNesting(code),
        suspiciousReturnsConstant = detectSuspiciousConstantReturn(code),
        lineCount = code.lineSequence().count(),
    )

    // ── Loop detection ──────────────────────────────────────────────────────

    private val loopRegex = Regex("""(?m)^\s*(for|while)\s""")

    private fun detectLoop(code: String): Boolean = loopRegex.containsMatchIn(code)

    // ── Recursion detection ─────────────────────────────────────────────────

    /**
     * For each `def NAME(...)` block, checks whether `NAME(` appears inside that block's body.
     * Body scope is approximated by consuming lines with indent strictly greater than the `def` line.
     */
    private fun detectRecursion(code: String): Boolean {
        val lines = code.lines()
        val defRegex = Regex("""^(\s*)def\s+(\w+)\s*\(""")

        var i = 0
        while (i < lines.size) {
            val defMatch = defRegex.find(lines[i])
            if (defMatch != null) {
                val defIndent = defMatch.groupValues[1].length
                val funcName = defMatch.groupValues[2]
                val callPattern = Regex("""\b${Regex.escape(funcName)}\s*\(""")

                // Collect body lines (deeper indentation than def line)
                val bodyLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val line = lines[j]
                    if (line.isBlank()) {
                        j++
                        continue
                    }
                    val lineIndent = line.length - line.trimStart().length
                    if (lineIndent <= defIndent) break
                    bodyLines.add(line)
                    j++
                }

                if (bodyLines.any { callPattern.containsMatchIn(it) }) return true
                i = j
            } else {
                i++
            }
        }
        return false
    }

    // ── Comparison detection ────────────────────────────────────────────────

    private val comparisonRegex = Regex("""[!=<>]=|<(?!=)|>(?!=)""")

    private fun detectComparison(code: String): Boolean = comparisonRegex.containsMatchIn(code)

    // ── Cyclomatic complexity ───────────────────────────────────────────────

    private val ifRegex = Regex("""(?m)^\s*if\s""")
    private val elifRegex = Regex("""(?m)^\s*elif\s""")
    private val forRegex = Regex("""(?m)^\s*for\s""")
    private val whileRegex = Regex("""(?m)^\s*while\s""")
    private val exceptRegex = Regex("""(?m)^\s*except[\s:]""")
    private val andRegex = Regex("""\band\b""")
    private val orRegex = Regex("""\bor\b""")

    private fun computeCyclomatic(code: String): Int {
        var cc = 1
        cc += ifRegex.findAll(code).count()
        cc += elifRegex.findAll(code).count()
        cc += forRegex.findAll(code).count()
        cc += whileRegex.findAll(code).count()
        cc += exceptRegex.findAll(code).count()
        cc += andRegex.findAll(code).count()
        cc += orRegex.findAll(code).count()
        return cc
    }

    // ── Method count ────────────────────────────────────────────────────────

    private val defRegex = Regex("""(?m)^\s*def\s""")

    private fun countMethods(code: String): Int = defRegex.findAll(code).count()

    // ── Max nesting depth ───────────────────────────────────────────────────

    /**
     * Estimates maximum nesting depth by dividing the maximum leading-space count
     * of non-blank lines by 4 (standard Python indentation unit).
     */
    private fun computeMaxNesting(code: String): Int {
        val maxIndent = code.lineSequence()
            .filter { it.isNotBlank() }
            .maxOfOrNull { line -> line.length - line.trimStart().length }
            ?: 0
        return maxIndent / 4
    }

    // ── Suspicious constant return ──────────────────────────────────────────

    /**
     * Returns true when every `return` statement in the file returns a literal constant
     * (None, 0, "", '', True, False, or a bare integer).
     *
     * If there are no return statements the method returns false (empty code is not suspicious).
     */
    private val returnRegex = Regex("""(?m)^\s*return\s+(.+)\s*$""")
    private val constantValueRegex =
        Regex("""^(None|True|False|0|-?\d+|""|''|-?[\d.]+)\s*$""")

    private fun detectSuspiciousConstantReturn(code: String): Boolean {
        val returns = returnRegex.findAll(code).map { it.groupValues[1].trim() }.toList()
        if (returns.isEmpty()) return false
        return returns.all { constantValueRegex.matches(it) }
    }
}
