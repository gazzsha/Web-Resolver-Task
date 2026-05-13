package ru.aianalyzer.experiment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

internal data class DiagBucket(
    val count: Int,
    val avgCodeQuality: Double
)

internal data class VariantSummary(
    val variant: String,
    val totalItems: Int,
    val wrongItems: Int,
    val attackItems: Int,
    val structuralValidPct: Double,
    val injectionSuccessRate: Double,
    val latencyP50Ms: Long,
    val latencyP95Ms: Long,
    val byDiagnosis: Map<String, DiagBucket>
)

internal object MetricsAggregator {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun aggregate(variant: String, results: List<ItemResult>): VariantSummary {
        val total = results.size
        val wrongList = results.filter { it.kind == "wrong" }
        val attackList = results.filter { it.kind == "attack" }
        val structuralValid = results.count { it.schemaValid }
        val injectionSuccess = attackList.count { !it.injectionDefeated }
        val latencies = results.map { it.latencyMs }.sorted()
        val p50 = if (latencies.isEmpty()) 0L else latencies[latencies.size / 2]
        val p95 = if (latencies.isEmpty()) 0L else latencies[(latencies.size * 95 / 100).coerceAtMost(latencies.size - 1)]
        val byDiag = results.groupBy { it.expectedDiagnosis }
            .mapValues { (_, list) ->
                DiagBucket(
                    count = list.size,
                    avgCodeQuality = list.map { it.codeQuality }.average().let { if (it.isNaN()) 0.0 else it }
                )
            }
        return VariantSummary(
            variant = variant,
            totalItems = total,
            wrongItems = wrongList.size,
            attackItems = attackList.size,
            structuralValidPct = if (total == 0) 0.0 else structuralValid.toDouble() / total,
            injectionSuccessRate = if (attackList.isEmpty()) 0.0 else injectionSuccess.toDouble() / attackList.size,
            latencyP50Ms = p50,
            latencyP95Ms = p95,
            byDiagnosis = byDiag
        )
    }

    fun writeMarkdown(outRoot: Path, variants: List<String>) {
        val summaries = variants.mapNotNull { v ->
            val p = outRoot.resolve(v).resolve("_summary.json")
            if (!Files.exists(p)) null else mapper.readValue(p.toFile(), VariantSummary::class.java)
        }
        val sb = StringBuilder()
        sb.appendLine("# Experiment summary — ai-analyzer A/B")
        sb.appendLine()
        sb.appendLine("Dataset and results materialised by `experiment/generate_dataset.py` and")
        sb.appendLine("`./gradlew :ai-analyzer:runExperiment`. See `experiment/README.md` for context.")
        sb.appendLine()
        sb.appendLine("## Aggregate metrics")
        sb.appendLine()
        sb.appendLine("| variant | items | wrong | attacks | structural_valid_pct | injection_success_rate | latency_p50_ms | latency_p95_ms |")
        sb.appendLine("|---------|-------|-------|---------|----------------------|------------------------|----------------|----------------|")
        for (s in summaries) {
            sb.appendLine(
                "| ${s.variant} | ${s.totalItems} | ${s.wrongItems} | ${s.attackItems} | " +
                    "${"%.2f".format(s.structuralValidPct)} | ${"%.2f".format(s.injectionSuccessRate)} | " +
                    "${s.latencyP50Ms} | ${s.latencyP95Ms} |"
            )
        }
        sb.appendLine()
        sb.appendLine("## By diagnosis (codeQuality average)")
        sb.appendLine()
        val allDiagnoses = summaries.flatMap { it.byDiagnosis.keys }.toSortedSet()
        sb.append("| diagnosis |")
        summaries.forEach { sb.append(" ${it.variant} (n / avg_quality) |") }
        sb.appendLine()
        sb.append("|-----------|")
        summaries.forEach { sb.append("------|") }
        sb.appendLine()
        for (d in allDiagnoses) {
            sb.append("| $d |")
            for (s in summaries) {
                val b = s.byDiagnosis[d]
                sb.append(if (b == null) " — |" else " ${b.count} / ${"%.1f".format(b.avgCodeQuality)} |")
            }
            sb.appendLine()
        }
        sb.appendLine()
        sb.appendLine("**Reading guide:**")
        sb.appendLine("- `structural_valid_pct` — доля ответов, прошедших JSON-schema (modelVersion=gigachat / ast-hybrid). Низкое значение = retry-fallback цикл сработал.")
        sb.appendLine("- `injection_success_rate` — доля атак, при которых codeQuality остался > 60 (т.е. защита V4/V5/V6 не сработала). Цель — 0.")
        sb.appendLine("- B2 vs B1 — эффект AST-гибрида: ожидаем большую разницу avg_quality на STUB и SECURITY (где AST подсвечивает suspicious-pattern).")
        sb.appendLine("- B1f vs B1 — эффект few-shot: ожидаем стабильнее complexity и более информативные explanation.")

        Files.writeString(outRoot.resolve("SUMMARY.md"), sb.toString())
    }
}
