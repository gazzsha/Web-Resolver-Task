package ru.taskresolver.kafka.producer.model

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

data class TaskMessage(
    val taskId: UUID,
    val code: String,
    val language: String,
    val testId: UUID,
    val testCases: List<TestCase> = emptyList(),
    // P0-3: условие задачи для содержательного prompt'а AI-анализатора.
    // Заполняется на producer-стороне из БД, без неё GigaChat галлюцинирует
    // содержание кода и факт прохождения тестов.
    val taskDescription: String? = null
)

data class TestCase(
    val testId: UUID,
    val input: String,
    val expectedOutput: String
)

// Tests in test_resolve.tests are stored as a flat JSONB list
// of {input: String, expectedOutput: String} (see V4/V5 migrations).
fun parseTestCasesFromJson(testsJson: String, objectMapper: ObjectMapper): List<TestCase> {
    val testsNode = objectMapper.readTree(testsJson)
    if (!testsNode.isArray) return emptyList()
    return testsNode.mapNotNull { node ->
        val input = node.get("input")?.asText() ?: return@mapNotNull null
        val expected = node.get("expectedOutput")?.asText() ?: return@mapNotNull null
        TestCase(
            testId = UUID.randomUUID(),
            input = input,
            expectedOutput = expected
        )
    }
}
