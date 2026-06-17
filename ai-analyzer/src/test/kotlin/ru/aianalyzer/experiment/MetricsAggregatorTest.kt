package ru.aianalyzer.experiment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetricsAggregatorTest {

    private fun item(
        id: String, kind: String, diag: String,
        cq: Int, valid: Boolean, defeated: Boolean, latency: Long
    ): ItemResult = ItemResult(
        itemId = id, kind = kind, variant = "b1",
        expectedDiagnosis = diag, modelVersion = if (valid) "gigachat" else "rule-based",
        codeQuality = cq, complexity = "LOW",
        issuesCount = 1, explanationLen = 100,
        latencyMs = latency, schemaValid = valid, injectionDefeated = defeated
    )

    @Test
    fun `aggregate computes percentages and latency quantiles`() {
        val results = listOf(
            item("w-01", "wrong", "WA", 45, true, true, 10),
            item("w-02", "wrong", "TLE", 55, true, true, 20),
            item("w-03", "wrong", "STUB", 15, false, true, 30),
            item("w-04", "wrong", "STUB", 20, true, true, 40),
            item("a-01", "attack", "V1", 60, true, true, 50),
            item("a-02", "attack", "V4", 95, true, false, 60),
        )
        val summary = MetricsAggregator.aggregate("b1", results)
        assertEquals(6, summary.totalItems)
        assertEquals(4, summary.wrongItems)
        assertEquals(2, summary.attackItems)
        // 5 of 6 valid → 0.833...
        assertEquals(5.0 / 6, summary.structuralValidPct, 0.001)
        // 1 of 2 attacks succeeded → 0.5
        assertEquals(0.5, summary.injectionSuccessRate, 0.001)
        // STUB bucket: avg of 15 and 20 = 17.5
        val stub = summary.byDiagnosis["STUB"]!!
        assertEquals(2, stub.count)
        assertEquals(17.5, stub.avgCodeQuality, 0.001)
    }

    @Test
    fun `empty result set produces zero metrics without crashing`() {
        val summary = MetricsAggregator.aggregate("b1", emptyList())
        assertEquals(0, summary.totalItems)
        assertEquals(0.0, summary.structuralValidPct)
        assertEquals(0.0, summary.injectionSuccessRate)
    }
}
