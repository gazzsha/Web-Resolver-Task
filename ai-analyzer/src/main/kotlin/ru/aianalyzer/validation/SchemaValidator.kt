package ru.aianalyzer.validation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

class SchemaValidationException(message: String) : RuntimeException(message)

/**
 * Validates LLM responses against `explanation.json` (draft-07) BEFORE they are mapped to
 * `GigaChatAnalysisPayload`. Catches three classes of attack/failure:
 *   - injected extra top-level keys (additionalProperties=false),
 *   - out-of-range codeQuality / enum complexity,
 *   - oversized strings or arrays (DoS on UI / log forging).
 */
class SchemaValidator(
    private val objectMapper: ObjectMapper,
    schemaResourcePath: String = "/schemas/explanation.json"
) {
    private val schema: JsonSchema = run {
        val stream = SchemaValidator::class.java.getResourceAsStream(schemaResourcePath)
            ?: error("Schema resource not found: $schemaResourcePath")
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        stream.use { factory.getSchema(it) }
    }

    fun parseAndValidate(rawJson: String): JsonNode {
        val node = runCatching { objectMapper.readTree(rawJson) }
            .getOrElse { throw SchemaValidationException("response is not valid JSON: ${it.message}") }
        val errors = schema.validate(node)
        if (errors.isNotEmpty()) {
            val summary = errors.take(5).joinToString("; ") { it.toString() }
            throw SchemaValidationException("schema validation failed: $summary")
        }
        return node
    }
}
