package ru.taskresolver.kafka.producer.model

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

data class TaskMessage(
    val taskId: UUID,
    val code: String,
    val language: String,
    val testId: UUID,
    val testCases: List<TestCase> = emptyList()
)

data class TestCase(
    val testId: UUID,
    val input: String,
    val expectedOutput: String
)

fun parseTestCasesFromJson(testsJson: String, objectMapper: ObjectMapper): List<TestCase> {
    val testsNode = objectMapper.readTree(testsJson)
    return testsNode.map { node ->
        val inputNode = node.get("input")
        val outputNode = node.get("output")
        
        // Convert input map to string format: "nums=[2,7,11,15]\ntarget=9"
        val inputString = inputNode.fields().asSequence().joinToString("\n") { field ->
            val value = field.value
            val formattedValue = if (value.isArray) {
                value.joinToString(",") { it.asInt().toString() }
            } else {
                value.asText()
            }
            "${field.key}=[$formattedValue]"
        }
        
        // Convert output to string
        val outputString = outputNode.fields().asSequence().firstOrNull()?.let { field ->
            if (field.value.isArray) {
                field.value.joinToString(",") { it.asInt().toString() }
            } else {
                field.value.asText()
            }
        } ?: ""
        
        TestCase(
            testId = UUID.randomUUID(),
            input = inputString,
            expectedOutput = outputString
        )
    }
}
