package ru.aianalyzer.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GigaChatTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_at") val expiresAt: Long? = null
)

data class GigaChatMessage(
    val role: String,
    val content: String
)

data class GigaChatChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Double = 0.2,
    @JsonProperty("top_p") val topP: Double = 0.9,
    val stream: Boolean = false,
    @JsonProperty("max_tokens") val maxTokens: Int = 1024
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GigaChatChatResponse(
    val choices: List<GigaChatChoice> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GigaChatChoice(
    val message: GigaChatMessage? = null,
    @JsonProperty("finish_reason") val finishReason: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GigaChatAnalysisPayload(
    val codeQuality: Int = 50,
    val issues: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val explanation: String = "",
    val complexity: String = "MEDIUM"
)
