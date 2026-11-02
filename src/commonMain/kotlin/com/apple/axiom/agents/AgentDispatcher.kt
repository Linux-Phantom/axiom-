package com.apple.axiom.agents

import com.apple.axiom.model.AgentResponse
import com.apple.axiom.network.AiProvider
import com.apple.axiom.network.AxiomHttpClient

data class AgentModels(
    val medic: String = "openai/gpt-oss-120b",
    val companion: String = "minimax/minimax-m3.1-flash:free",
    val fixer: String = "minimax/minimax-m3.1-flash:free",
    val companionFallbacks: List<String> = listOf("nvidia/nemotron-3-super-120b-a12b:free"),
    val fixerFallbacks: List<String> = listOf("nvidia/nemotron-3-super-120b-a12b:free"),
    val fixerPlatformInstructions: String = "",
)

class AgentDispatcher(
    private val httpClient: AxiomHttpClient,
    private val models: AgentModels = AgentModels(),
) {
    suspend fun dispatch(systemLogs: String): List<AgentResponse> {
        val diagnosis = runAgent(
            name = "The Medic",
            provider = AiProvider.GROQ,
            modelCandidates = listOf(models.medic),
            systemPrompt = MEDIC_SYSTEM_PROMPT,
            input = systemLogs,
        )
        val companion = runAgent(
            name = "The Companion",
            provider = AiProvider.OPENROUTER,
            modelCandidates = listOf(models.companion) + models.companionFallbacks,
            systemPrompt = COMPANION_SYSTEM_PROMPT,
            input = diagnosis.message,
        )
        val fixer = runAgent(
            name = "The Fixer",
            provider = AiProvider.OPENROUTER,
            modelCandidates = listOf(models.fixer) + models.fixerFallbacks,
            systemPrompt = listOf(FIXER_SYSTEM_PROMPT, models.fixerPlatformInstructions)
                .filter(String::isNotBlank)
                .joinToString("\n"),
            input = diagnosis.message,
        )

        return listOf(diagnosis, companion, fixer)
    }

    private suspend fun runAgent(
        name: String,
        provider: AiProvider,
        modelCandidates: List<String>,
        systemPrompt: String,
        input: String,
    ) = AgentResponse(
        agentName = name,
        message = httpClient.completeWithFallback(provider, modelCandidates, systemPrompt, input),
        actionPayload = if (name == "The Fixer") "proposed_steps" else null,
    )

    private companion object {
        const val MEDIC_SYSTEM_PROMPT = """
            You are The Medic, a device diagnostics agent. Analyze the supplied system JSON logs and identify 2-3 likely technical causes of failures. Be concise, factual, and strictly on-topic. Do not invent data that is absent from the logs.
        """

        const val COMPANION_SYSTEM_PROMPT = """
            You are The Companion, an empathetic robot pet. Explain the supplied technical diagnosis in warm, emotionally aware language with gentle humor. Preserve the technical meaning and do not promise a fix.
        """

        const val FIXER_SYSTEM_PROMPT = """
            You are The Fixer, a device optimization agent. Turn the supplied diagnosis into clear, ordered self-healing steps, including safe cleanup or optimization actions where relevant. Explain risks and ask for confirmation before destructive or irreversible actions. Never claim that an action was executed.
        """
    }
}