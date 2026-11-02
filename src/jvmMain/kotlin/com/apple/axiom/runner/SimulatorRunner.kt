package com.apple.axiom.runner

import com.apple.axiom.agents.AgentDispatcher
import com.apple.axiom.agents.AgentModels
import com.apple.axiom.network.AiProvider
import com.apple.axiom.network.AxiomHttpClient
import com.apple.axiom.simulator.DeviceMetricsSimulator
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties

fun main(args: Array<String>) = runBlocking {
    val openRouterKeys = configuredOpenRouterKeys()
    check(openRouterKeys.isNotEmpty()) {
        "Set OPENROUTER_API_KEY[_1/_2] or configure openrouter_api_key.1/.2 in local.properties"
    }

    val smokeTest = "--openrouter-smoke" in args
    val groqApiKey = if (smokeTest) null else requiredSetting("GROQ_API_KEY", "groq_api_key")
    val configuredModels = configuredModels()

    val httpClient = AxiomHttpClient(
        engine = CIO.create(),
        apiKeys = buildMap {
            put(AiProvider.OPENROUTER, openRouterKeys.first())
            groqApiKey?.let { put(AiProvider.GROQ, it) }
        },
        fallbackApiKeys = mapOf(AiProvider.OPENROUTER to openRouterKeys.drop(1)),
    )

    try {
        val dispatcher = AgentDispatcher(
            httpClient = httpClient,
            models = configuredModels,
        )

        if (smokeTest) {
            val response = httpClient.completeWithFallback(
                provider = AiProvider.OPENROUTER,
                models = listOf(configuredModels.companion) + configuredModels.companionFallbacks,
                systemPrompt = "You are a concise assistant.",
                userMessage = "Reply with exactly AXIOM_OPENROUTER_SMOKE_OK",
            )
            println("OpenRouter smoke test succeeded; response characters: ${response.length}")
            return@runBlocking
        }

        val simulator = DeviceMetricsSimulator()
        simulator.generateScenarios().forEach { scenario ->
            println("\n=== Scenario: ${scenario.name} ===")
            println("${scenario.description}")

            val responses = dispatcher.dispatch(simulator.toSystemLog(scenario))
            responses.forEach { response ->
                println("\n[${response.agentName}]")
                println(response.message)
                response.actionPayload?.let { println("Action payload: $it") }
            }
        }
    } finally {
        httpClient.close()
    }
}

private val localProperties by lazy {
    Properties().apply {
        val file = File("local.properties")
        if (file.isFile) file.inputStream().use(::load)
    }
}

private fun configuredOpenRouterKeys(): List<String> = listOfNotNull(
    setting("OPENROUTER_API_KEY", "openrouter_api_key", "openrouter.api.key"),
    setting("OPENROUTER_API_KEY_1", "openrouter_api_key.1", "openrouter.api.key.1"),
    setting("OPENROUTER_API_KEY_2", "openrouter_api_key.2", "openrouter.api.key.2"),
).distinct()

private fun configuredModels(): AgentModels {
    val defaults = AgentModels()
    return AgentModels(
        medic = setting("AXIOM_MEDIC_MODEL", "axiom.medic.model") ?: defaults.medic,
        companion = setting("AXIOM_COMPANION_MODEL", "axiom.companion.model") ?: defaults.companion,
        fixer = setting("AXIOM_FIXER_MODEL", "axiom.fixer.model") ?: defaults.fixer,
        companionFallbacks = setting("AXIOM_COMPANION_FALLBACK_MODELS", "axiom.companion.fallback.models")
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
            ?: defaults.companionFallbacks,
        fixerFallbacks = setting("AXIOM_FIXER_FALLBACK_MODELS", "axiom.fixer.fallback.models")
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
            ?: defaults.fixerFallbacks,
    )
}

private fun requiredSetting(environmentName: String, vararg propertyNames: String): String =
    setting(environmentName, *propertyNames)
        ?: error("Set $environmentName or configure its property in ignored local.properties")

private fun setting(environmentName: String, vararg propertyNames: String): String? =
    System.getenv(environmentName)?.takeIf(String::isNotBlank)
        ?: propertyNames.firstNotNullOfOrNull { propertyName ->
            localProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
        }