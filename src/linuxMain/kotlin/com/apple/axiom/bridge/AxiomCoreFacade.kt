package com.apple.axiom.bridge

import com.apple.axiom.agents.AgentDispatcher
import com.apple.axiom.agents.AgentModels
import com.apple.axiom.model.AxiomCoreAction
import com.apple.axiom.model.AxiomCoreActionResult
import com.apple.axiom.model.AxiomCoreUpdate
import com.apple.axiom.model.DeviceMetrics
import com.apple.axiom.network.AiProvider
import com.apple.axiom.network.AxiomHttpClient
import io.ktor.client.engine.curl.Curl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AxiomCoreFacade(
    groqApiKey: String,
    openRouterApiKey: String,
    openRouterFallbackApiKey: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val httpClient = AxiomHttpClient(
        engine = Curl.create(),
        apiKeys = mapOf(
            AiProvider.GROQ to groqApiKey,
            AiProvider.OPENROUTER to openRouterApiKey,
        ),
        fallbackApiKeys = mapOf(
            AiProvider.OPENROUTER to listOfNotNull(openRouterFallbackApiKey),
        ),
    )
    private val dispatcher = AgentDispatcher(httpClient, AgentModels())
    private val json = Json
    private var latestFixerMessage = ""
    private var latestActions: Map<String, AxiomCoreAction> = emptyMap()

    fun analyze(
        metrics: DeviceMetrics,
        completion: (AxiomCoreUpdate?, String?) -> Unit,
    ) {
        scope.launch {
            try {
                val responses = dispatcher.dispatch(json.encodeToString(metrics))
                val companionMessage = responses.first { it.agentName == "The Companion" }.message
                val fixerMessage = responses.first { it.agentName == "The Fixer" }.message
                val actions = createActions(metrics, fixerMessage)
                latestFixerMessage = fixerMessage
                latestActions = actions.associateBy(AxiomCoreAction::id)

                completion(
                    AxiomCoreUpdate(
                        metrics = metrics,
                        mood = moodFor(metrics),
                        companionMessage = companionMessage,
                        fixerMessage = fixerMessage,
                        actions = actions,
                        updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                    null,
                )
            } catch (error: Throwable) {
                completion(null, error.message ?: "Axiom Core analysis failed")
            }
        }
    }

    fun performAction(actionId: String): AxiomCoreActionResult? {
        val action = latestActions[actionId] ?: return null
        val message = when (actionId) {
            REVIEW_FIXER_PLAN -> latestFixerMessage
            REDUCE_LOAD -> "Simulation only: a CPU-load recommendation was prepared. No process was stopped."
            REVIEW_STORAGE -> "Simulation only: storage-cleanup options were prepared. No files were removed."
            BATTERY_CARE -> "Simulation only: battery recommendations were prepared. Power settings were not changed."
            else -> return null
        }
        return AxiomCoreActionResult(action.id, message, simulated = true)
    }

    fun close() {
        scope.cancel()
        httpClient.close()
    }

    private fun createActions(metrics: DeviceMetrics, fixerMessage: String): List<AxiomCoreAction> = buildList {
        add(
            AxiomCoreAction(
                id = REVIEW_FIXER_PLAN,
                title = "The Fixer plan",
                detail = fixerMessage,
                confirmationMessage = "Show the proposed steps. No system changes will be made.",
            ),
        )
        if (metrics.cpuTemperature >= HOT_CPU_CELSIUS) {
            add(AxiomCoreAction(REDUCE_LOAD, "Review CPU load", "Prepare safe load-reduction recommendations.", "Confirm simulation only."))
        }
        if (metrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES) {
            add(AxiomCoreAction(REVIEW_STORAGE, "Review disk cleanup", "List safe cleanup options without deleting files.", "No files will be removed automatically."))
        }
        if (metrics.batteryHealthPercent in 0 until LOW_BATTERY_HEALTH_PERCENT) {
            add(AxiomCoreAction(BATTERY_CARE, "Review battery health", "Prepare battery-care recommendations.", "No power settings will be changed."))
        }
    }

    private fun moodFor(metrics: DeviceMetrics): String = when {
        metrics.cpuTemperature >= HOT_CPU_CELSIUS -> "anxious"
        metrics.batteryHealthPercent in 0 until LOW_BATTERY_HEALTH_PERCENT ||
            metrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES -> "sad"
        else -> "happy"
    }

    private companion object {
        const val REVIEW_FIXER_PLAN = "review-fixer-plan"
        const val REDUCE_LOAD = "reduce-load"
        const val REVIEW_STORAGE = "review-storage"
        const val BATTERY_CARE = "battery-care"
        const val HOT_CPU_CELSIUS = 85.0
        const val LOW_STORAGE_GIGABYTES = 8.0
        const val LOW_BATTERY_HEALTH_PERCENT = 70
    }
}