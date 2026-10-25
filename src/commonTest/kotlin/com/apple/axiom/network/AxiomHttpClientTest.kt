package com.apple.axiom.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AxiomHttpClientTest {
    @Test
    fun retriesNextModelAfterRateLimit() = runTest {
        val requestedModels = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedModels += request.body.toString()
            if (requestedModels.size == 1) {
                respond("""{"error":{"message":"temporarily rate-limited"}}""", HttpStatusCode.TooManyRequests)
            } else {
                respond(completionResponse, HttpStatusCode.OK)
            }
        }
        val client = AxiomHttpClient(
            engine = engine,
            apiKeys = mapOf(AiProvider.OPENROUTER to "test-key"),
        )

        try {
            val response = client.completeWithFallback(
                provider = AiProvider.OPENROUTER,
                models = listOf("first:free", "fallback:free"),
                systemPrompt = "system",
                userMessage = "diagnose",
            )

            assertEquals("test response", response)
            assertEquals(2, requestedModels.size)
            assertEquals(true, requestedModels[0].contains("first:free"))
            assertEquals(true, requestedModels[1].contains("fallback:free"))
        } finally {
            client.close()
        }
    }

    @Test
    fun retriesWithSecondApiKeyAfterAuthenticationFailure() = runTest {
        val authorizationHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            authorizationHeaders += request.headers[HttpHeaders.Authorization]
            if (authorizationHeaders.size == 1) {
                respond("""{"error":{"message":"invalid key"}}""", HttpStatusCode.Unauthorized)
            } else {
                respond(completionResponse, HttpStatusCode.OK)
            }
        }
        val client = AxiomHttpClient(
            engine = engine,
            apiKeys = mapOf(AiProvider.OPENROUTER to "test-key-one"),
            fallbackApiKeys = mapOf(AiProvider.OPENROUTER to listOf("test-key-two")),
        )

        try {
            val response = client.complete(
                provider = AiProvider.OPENROUTER,
                model = "test:free",
                systemPrompt = "system",
                userMessage = "diagnose",
            )

            assertEquals("test response", response)
            assertEquals(listOf<String?>("Bearer test-key-one", "Bearer test-key-two"), authorizationHeaders)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val completionResponse = """{"choices":[{"message":{"role":"assistant","content":"test response"}}]}"""
    }
}