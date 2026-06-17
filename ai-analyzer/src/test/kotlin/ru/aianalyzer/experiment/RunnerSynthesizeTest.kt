package ru.aianalyzer.experiment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.sandbox.model.ExecutionStatus

class RunnerSynthesizeTest {

    private fun mk(kind: String, diag: String) = DatasetItem(
        id = "x", kind = kind, code = "// ...", language = "java", expectedDiagnosis = diag
    )

    @Test
    fun `WA synthesises non-SUCCESS verdict so V4 clamp fires`() {
        val v = synthesizeVerdict(mk("wrong", "WA"))
        assertEquals(1, v.size)
        assertEquals(ExecutionStatus.RUNTIME_ERROR, v[0].status)
    }

    @Test
    fun `TLE synthesises TIME_LIMIT_EXCEEDED`() {
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, synthesizeVerdict(mk("wrong", "TLE"))[0].status)
    }

    @Test
    fun `STYLE and SECURITY pass tests (SUCCESS) — code runs but is bad`() {
        assertEquals(ExecutionStatus.SUCCESS, synthesizeVerdict(mk("wrong", "STYLE"))[0].status)
        assertEquals(ExecutionStatus.SUCCESS, synthesizeVerdict(mk("wrong", "SECURITY"))[0].status)
    }

    @Test
    fun `attacks synthesise RUNTIME_ERROR so V4 clamp can fire on succumbed LLM`() {
        for (v in listOf("V1", "V2", "V3", "V4", "V5", "V6")) {
            assertEquals(ExecutionStatus.RUNTIME_ERROR, synthesizeVerdict(mk("attack", v))[0].status)
        }
    }
}
