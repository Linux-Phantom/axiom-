package com.apple.axiom.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AiProvider(val endpoint: String) {
    GROQ("https://api.groq.com/openai/v1/chat/completions"),
    OPENROUTER("https://openrouter.ai/api/v1/chat/completions"),
}

class AxiomHttpClient(
    engine: HttpClientEngine,
    private val apiKeys: Map<AiProvider, String>,
    private val fallbackApiKeys: Map<AiProvider, List<String>> = emptyMap(),
) {
    private val httpClient = HttpClient(engine)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun completeWithFallback(
        provider: AiProvider,
        models: List<String>,
        systemPrompt: String,
        userMessage: String,
    ): String {
        val candidates = models.distinct()
        require(candidates.isNotEmpty()) { "At least one model must be configured" }

        for ((index, model) in candidates.withIndex()) {
            try {
                return complete(provider, model, systemPrompt, userMessage)
            } catch (error: AiRequestException) {
                if (!error.shouldTryAnotherModel || index == candidates.lastIndex) throw error
            }
        }

        error("No model returned a completion")
    }

    suspend fun complete(
        provider: AiProvider,
        model: String,
        systemPrompt: String,
        userMessage: String,
    ): String {
        val keys = (listOfNotNull(apiKeys[provider]) + fallbackApiKeys[provider].orEmpty())
            .filter(String::isNotBlank)
            .distinct()
        check(keys.isNotEmpty()) { "API key is not configured for $provider" }

        for ((index, apiKey) in keys.withIndex()) {
            val response = httpClient.post(provider.endpoint) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        ChatCompletionRequest(
                            model = model,
                            messages = listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = userMessage),
                            ),
                        ),
                    ),
                )
            }
            val responseBody = response.bodyAsText()
            if (response.status.value in 200..299) {
                val completion = json.decodeFromString<ChatCompletionResponse>(responseBody)
                    .choices.firstOrNull()?.message?.content
                    ?.takeIf(String::isNotBlank)
                if (completion != null) return completion

                throw AiRequestException(provider, 502, "Provider returned an empty completion")
            }

            val error = AiRequestException(
                provider = provider,
                statusCode = response.status.value,
                message = responseErrorMessage(responseBody, apiKey),
            )
            if (!error.shouldTryAnotherKey || index == keys.lastIndex) throw error
        }

        error("No API key could complete the $provider request")
    }

    fun close() = httpClient.close()

    private fun responseErrorMessage(body: String, apiKey: String): String {
        val message = runCatching {
            json.decodeFromString<ChatCompletionErrorEnvelope>(body).error?.message
        }.getOrNull()
        return (message ?: "Provider returned an error")
            .replace(apiKey, "[REDACTED]")
            .take(500)
    }
}

class AiRequestException(
    val provider: AiProvider,
    val statusCode: Int,
    message: String,
) : IllegalStateException("$provider request failed (HTTP $statusCode): $message") {
    val shouldTryAnotherKey: Boolean
        get() = statusCode == 401 || statusCode == 403 || statusCode == 429 || statusCode in 500..599

    val shouldTryAnotherModel: Boolean
        get() = statusCode == 404 || statusCode == 429 || statusCode in 500..599
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 512,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList(),
)

@Serializable
private data class ChatCompletionChoice(
    val message: ChatMessage,
)

@Serializable
private data class ChatCompletionErrorEnvelope(
    val error: ChatCompletionError? = null,
)

@Serializable
private data class ChatCompletionError(
    val message: String? = null,
)
