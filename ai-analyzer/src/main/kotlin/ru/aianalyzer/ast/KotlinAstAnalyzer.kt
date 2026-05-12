package ru.aianalyzer.ast

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.lexer.KtTokens

private val log = KotlinLogging.logger {}

/**
 * Extracts [AstFact] metrics from Kotlin source code using the Kotlin PSI (compiler-embeddable).
 *
 * The [KotlinCoreEnvironment] is initialised lazily on first use and reused across calls.
 * If initialisation fails (e.g. incompatible JVM), [analyze] returns [AstFact.empty] silently.
 *
 * Thread-safety: [KtPsiFactory] is NOT thread-safe. Callers should not share a single instance
 * across concurrent threads without external synchronisation. For the diploma project's
 * single-threaded analysis pipeline this is fine.
 */
internal class KotlinAstAnalyzer {

    private val psiFactory: KtPsiFactory? by lazy { createPsiFactory() }

    private fun createPsiFactory(): KtPsiFactory? = try {
        val disposable = Disposer.newDisposable()
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "ast-analysis")
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val env = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        KtPsiFactory(env.project)
    } catch (e: Exception) {
        log.warn(e) { "KotlinCoreEnvironment initialisation failed — Kotlin AST analysis disabled" }
        null
    }

    fun analyze(code: String): AstFact {
        val factory = psiFactory ?: return AstFact.empty("kotlin")
        return try {
            val file: KtFile = factory.createFile("Snippet.kt", code)
            extractFacts(code, file)
        } catch (e: Exception) {
            log.warn(e) { "Kotlin PSI analysis failed" }
            AstFact.empty("kotlin")
        }
    }

    private fun extractFacts(code: String, file: KtFile): AstFact {
        val functions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)

        return AstFact(
            language = "kotlin",
            hasLoop = detectLoop(file),
            hasRecursion = detectRecursion(functions),
            hasComparison = detectComparison(file),
            cyclomaticComplexity = computeCyclomatic(file),
            methodCount = functions.size,
            maxNestingDepth = computeMaxNesting(file),
            suspiciousReturnsConstant = detectSuspiciousConstantReturn(functions),
            lineCount = code.lineSequence().count(),
        )
    }

    // ── Loop detection ──────────────────────────────────────────────────────

    private fun detectLoop(file: KtFile): Boolean =
        PsiTreeUtil.findChildOfType(file, KtForExpression::class.java) != null ||
            PsiTreeUtil.findChildrenOfType(file, KtWhileExpression::class.java).isNotEmpty() ||
            PsiTreeUtil.findChildrenOfType(file, KtDoWhileExpression::class.java).isNotEmpty()

    // ── Recursion detection ─────────────────────────────────────────────────

    private fun detectRecursion(functions: Collection<KtNamedFunction>): Boolean =
        functions.any { func ->
            val name = func.name ?: return@any false
            val body = func.bodyExpression ?: func.bodyBlockExpression ?: return@any false
            PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java)
                .any { call -> call.calleeExpression?.text == name }
        }

    // ── Comparison detection ────────────────────────────────────────────────

    private val comparisonTokens = setOf(
        KtTokens.EQEQ,
        KtTokens.EXCLEQ,
        KtTokens.LT,
        KtTokens.GT,
        KtTokens.LTEQ,
        KtTokens.GTEQ,
    )

    private fun detectComparison(file: KtFile): Boolean =
        PsiTreeUtil.findChildrenOfType(file, KtBinaryExpression::class.java)
            .any { it.operationToken in comparisonTokens }

    // ── Cyclomatic complexity ───────────────────────────────────────────────

    private val logicalTokens = setOf(KtTokens.ANDAND, KtTokens.OROR)

    private fun computeCyclomatic(file: KtFile): Int {
        var cc = 1
        cc += PsiTreeUtil.findChildrenOfType(file, KtIfExpression::class.java).size
        cc += PsiTreeUtil.findChildrenOfType(file, KtForExpression::class.java).size
        cc += PsiTreeUtil.findChildrenOfType(file, KtWhileExpression::class.java).size
        cc += PsiTreeUtil.findChildrenOfType(file, KtDoWhileExpression::class.java).size
        cc += PsiTreeUtil.findChildrenOfType(file, KtWhenExpression::class.java).size
        cc += PsiTreeUtil.findChildrenOfType(file, KtBinaryExpression::class.java)
            .count { it.operationToken in logicalTokens }
        return cc
    }

    // ── Max nesting depth ───────────────────────────────────────────────────

    private fun isNestingNode(element: PsiElement): Boolean =
        element is KtIfExpression ||
            element is KtForExpression ||
            element is KtWhileExpression ||
            element is KtDoWhileExpression ||
            element is KtWhenExpression

    private fun computeMaxNesting(file: KtFile): Int {
        var maxDepth = 0

        fun dfs(node: PsiElement, depth: Int) {
            val nextDepth = if (isNestingNode(node)) depth + 1 else depth
            if (nextDepth > maxDepth) maxDepth = nextDepth
            node.children.forEach { dfs(it, nextDepth) }
        }

        file.children.forEach { dfs(it, 0) }
        return maxDepth
    }

    // ── Suspicious constant return ──────────────────────────────────────────

    private val skipFunctionNames = setOf("main", "toString", "hashCode", "equals")

    /**
     * A return expression is "constant" if its returned value is a literal or null.
     * Heuristic: text matches simple literal patterns.
     */
    private fun isConstantReturn(ret: KtReturnExpression): Boolean {
        val returnedText = ret.returnedExpression?.text?.trim() ?: return true // bare return
        return constantLiteralRegex.matches(returnedText)
    }

    private val constantLiteralRegex = Regex(
        """^(null|true|false|0|-?\d+|"[^"]*"|'[^']*'|-?[\d.]+[fFdDlL]?)$"""
    )

    private fun detectSuspiciousConstantReturn(functions: Collection<KtNamedFunction>): Boolean {
        val candidates = functions.filter { it.name !in skipFunctionNames }
        if (candidates.isEmpty()) return false

        return candidates.any { func ->
            val body: PsiElement = func.bodyExpression ?: func.bodyBlockExpression ?: return@any false
            val returns = PsiTreeUtil.findChildrenOfType(body, KtReturnExpression::class.java)
            returns.isNotEmpty() && returns.all { isConstantReturn(it) }
        }
    }
}
