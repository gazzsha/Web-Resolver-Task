package ru.aianalyzer.ast

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.DoStmt
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.stmt.ForStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.stmt.WhileStmt

/**
 * Extracts [AstFact] metrics from Java source code using javaparser.
 *
 * All operations are pure (no I/O). Callers should wrap invocations in try/catch
 * and fall back to [AstFact.empty] on any [Exception].
 */
internal class JavaAstAnalyzer {

    fun analyze(code: String): AstFact {
        val cu: CompilationUnit = StaticJavaParser.parse(code)

        return AstFact(
            language = "java",
            hasLoop = detectLoop(cu),
            hasRecursion = detectRecursion(cu),
            hasComparison = detectComparison(cu),
            cyclomaticComplexity = computeCyclomatic(cu),
            methodCount = cu.findAll(MethodDeclaration::class.java).size,
            maxNestingDepth = computeMaxNesting(cu),
            suspiciousReturnsConstant = detectSuspiciousConstantReturn(cu),
            lineCount = code.lineSequence().count(),
        )
    }

    // ── Loop detection ──────────────────────────────────────────────────────

    private fun detectLoop(cu: CompilationUnit): Boolean =
        cu.findFirst(ForStmt::class.java).isPresent ||
            cu.findFirst(WhileStmt::class.java).isPresent ||
            cu.findFirst(DoStmt::class.java).isPresent ||
            cu.findFirst(ForEachStmt::class.java).isPresent

    // ── Recursion detection ─────────────────────────────────────────────────

    private fun detectRecursion(cu: CompilationUnit): Boolean {
        return cu.findAll(MethodDeclaration::class.java).any { method ->
            val methodName = method.nameAsString
            method.body.map { body ->
                body.findAll(MethodCallExpr::class.java).any { call ->
                    call.nameAsString == methodName
                }
            }.orElse(false)
        }
    }

    // ── Comparison detection ────────────────────────────────────────────────

    private val comparisonOperators = setOf(
        BinaryExpr.Operator.EQUALS,
        BinaryExpr.Operator.NOT_EQUALS,
        BinaryExpr.Operator.LESS,
        BinaryExpr.Operator.GREATER,
        BinaryExpr.Operator.LESS_EQUALS,
        BinaryExpr.Operator.GREATER_EQUALS,
    )

    private fun detectComparison(cu: CompilationUnit): Boolean =
        cu.findAll(BinaryExpr::class.java).any { it.operator in comparisonOperators }

    // ── Cyclomatic complexity ───────────────────────────────────────────────

    private val logicalOperators = setOf(BinaryExpr.Operator.AND, BinaryExpr.Operator.OR)

    private fun computeCyclomatic(cu: CompilationUnit): Int {
        var cc = 1
        cc += cu.findAll(IfStmt::class.java).size
        cc += cu.findAll(ForStmt::class.java).size
        cc += cu.findAll(WhileStmt::class.java).size
        cc += cu.findAll(DoStmt::class.java).size
        cc += cu.findAll(ForEachStmt::class.java).size
        cc += cu.findAll(CatchClause::class.java).size
        cc += cu.findAll(SwitchEntry::class.java).size
        cc += cu.findAll(BinaryExpr::class.java).count { it.operator in logicalOperators }
        return cc
    }

    // ── Max nesting depth ───────────────────────────────────────────────────

    /**
     * Computes maximum block nesting depth via DFS.
     * Nesting-contributing node types: [IfStmt], [ForStmt], [WhileStmt], [DoStmt],
     * [ForEachStmt], [TryStmt], [BlockStmt] (inside a method body).
     */
    private fun computeMaxNesting(cu: CompilationUnit): Int {
        var maxDepth = 0

        fun dfs(node: Node, depth: Int) {
            val contributes = node is IfStmt ||
                node is ForStmt ||
                node is WhileStmt ||
                node is DoStmt ||
                node is ForEachStmt ||
                node is TryStmt
            val nextDepth = if (contributes) depth + 1 else depth
            if (nextDepth > maxDepth) maxDepth = nextDepth
            node.childNodes.forEach { dfs(it, nextDepth) }
        }

        cu.childNodes.forEach { dfs(it, 0) }
        return maxDepth
    }

    // ── Suspicious constant return ──────────────────────────────────────────

    private val skipMethodNames = setOf("main", "toString", "hashCode", "equals")

    private fun isConstantExpression(stmt: ReturnStmt): Boolean {
        val expr = stmt.expression.orElse(null) ?: return true // bare `return;`
        return expr is IntegerLiteralExpr ||
            expr is LongLiteralExpr ||
            expr is BooleanLiteralExpr ||
            expr is NullLiteralExpr ||
            expr is StringLiteralExpr
    }

    private fun detectSuspiciousConstantReturn(cu: CompilationUnit): Boolean {
        val candidates = cu.findAll(MethodDeclaration::class.java)
            .filter { it.nameAsString !in skipMethodNames }
        if (candidates.isEmpty()) return false

        return candidates.any { method ->
            val returns = method.body.map { body ->
                body.findAll(ReturnStmt::class.java)
            }.orElse(emptyList())

            returns.isNotEmpty() && returns.all { isConstantExpression(it) }
        }
    }
}
