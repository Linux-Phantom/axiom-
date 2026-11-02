package com.apple.axiom.platform

import com.apple.axiom.agents.AgentDispatcher
import com.apple.axiom.agents.AgentModels
import com.apple.axiom.model.AgentResponse
import com.apple.axiom.network.AiProvider
import com.apple.axiom.network.AxiomHttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.Properties

data class LinuxAgentResult(
    val medicMessage: String,
    val companionMessage: String,
    val fixerPlan: LinuxFixerPlan,
)

class LinuxAgentService(
    private val httpClient: AxiomHttpClient,
    private val models: AgentModels = AgentModels(
        fixerPlatformInstructions = LINUX_FIXER_INSTRUCTIONS,
    ),
    private val fixerPlanner: LinuxFixerPlanner = LinuxFixerPlanner(),
) : Closeable {
    private val dispatcher = AgentDispatcher(httpClient, models)

    fun analyze(snapshot: LinuxSystemSnapshot): LinuxAgentResult = runBlocking {
        val responses = dispatcher.dispatch(Json.encodeToString(snapshot))
        val byName = responses.associateBy(AgentResponse::agentName)
        val medic = requireNotNull(byName["The Medic"]) { "The Medic returned no response" }
        val companion = requireNotNull(byName["The Companion"]) { "The Companion returned no response" }
        val fixer = requireNotNull(byName["The Fixer"]) { "The Fixer returned no response" }

        LinuxAgentResult(
            medicMessage = medic.message,
            companionMessage = companion.message,
            fixerPlan = fixerPlanner.plan(snapshot, fixer.message),
        )
    }

    override fun close() = httpClient.close()

    private companion object {
        const val LINUX_FIXER_INSTRUCTIONS = """
            The target operating system is Linux. Explain safe, reversible diagnostic steps. Do not generate executable scripts, shell pipelines, commands with interpolated values, or commands that delete data or terminate processes. The application provides a separate fixed command-preview allowlist; never claim it has executed an action. Require manual review and explicit confirmation for any system-changing step.
        """
    }
}

object LinuxAgentServiceFactory {
    fun create(): LinuxAgentService {
        val properties = Properties().apply {
            val localFile = File("local.properties")
            if (localFile.isFile) localFile.inputStream().use(::load)
        }
        fun setting(environment: String, vararg propertyNames: String): String? =
            System.getenv(environment)?.takeIf(String::isNotBlank)
                ?: propertyNames.firstNotNullOfOrNull { properties.getProperty(it)?.takeIf(String::isNotBlank) }

        val groqKey = requireNotNull(setting("GROQ_API_KEY", "groq_api_key", "groq.api.key")) {
            "Set GROQ_API_KEY or add groq_api_key to local.properties"
        }
        val openRouterKeys = listOfNotNull(
            setting("OPENROUTER_API_KEY", "openrouter_api_key", "openrouter.api.key"),
            setting("OPENROUTER_API_KEY_1", "openrouter_api_key.1", "openrouter.api.key.1"),
            setting("OPENROUTER_API_KEY_2", "openrouter_api_key.2", "openrouter.api.key.2"),
        ).distinct()
        require(openRouterKeys.isNotEmpty()) {
            "Set an OPENROUTER_API_KEY or add openrouter_api_key.1 to local.properties"
        }

        val httpClient = AxiomHttpClient(
            engine = CIO.create(),
            apiKeys = mapOf(
                AiProvider.GROQ to groqKey,
                AiProvider.OPENROUTER to openRouterKeys.first(),
            ),
            fallbackApiKeys = mapOf(AiProvider.OPENROUTER to openRouterKeys.drop(1)),
        )
        return LinuxAgentService(httpClient)
    }
}