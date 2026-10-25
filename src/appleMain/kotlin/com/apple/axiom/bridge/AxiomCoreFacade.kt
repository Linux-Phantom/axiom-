package com.apple.axiom.bridge

import com.apple.axiom.agents.AgentDispatcher
import com.apple.axiom.agents.AgentModels
import com.apple.axiom.model.AxiomCoreAction
import com.apple.axiom.model.AxiomCoreActionResult
import com.apple.axiom.model.AxiomCoreUpdate
import com.apple.axiom.model.DeviceMetrics
import com.apple.axiom.network.AiProvider
import com.apple.axiom.network.AxiomHttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.datetime.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AxiomCoreFacade(
    groqApiKey: String,
    openRouterApiKey: String,
    openRouterFallbackApiKey: String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient = AxiomHttpClient(
        engine = Darwin.create(),
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
                completion(null, error.message ?: "Не удалось получить ответ от Axiom Core")
            }
        }
    }

    fun performAction(
        actionId: String,
        completion: (AxiomCoreActionResult?, String?) -> Unit,
    ) {
        scope.launch {
            val action = latestActions[actionId]
            if (action == null) {
                completion(null, "Действие устарело. Обновите состояние устройства и попробуйте снова.")
                return@launch
            }

            val message = when (actionId) {
                REVIEW_FIXER_PLAN -> latestFixerMessage
                REDUCE_LOAD -> "Симуляция: рекомендация по снижению нагрузки подготовлена. Процессы системы не изменялись."
                REVIEW_STORAGE -> "Симуляция: варианты освобождения места подготовлены. Файлы не удалялись."
                BATTERY_CARE -> "Симуляция: рекомендации по уходу за аккумулятором подготовлены. Настройки питания не менялись."
                else -> {
                    completion(null, "Для этого действия нет безопасного обработчика.")
                    return@launch
                }
            }
            completion(AxiomCoreActionResult(actionId, message, simulated = true), null)
        }
    }

    fun close() {
        scope.cancel()
        httpClient.close()
    }

    private fun createActions(metrics: DeviceMetrics, fixerMessage: String): List<AxiomCoreAction> = buildList {
        add(
            AxiomCoreAction(
                id = REVIEW_FIXER_PLAN,
                title = "План The Fixer",
                detail = fixerMessage,
                confirmationMessage = "Показать рекомендации The Fixer. Системные настройки не будут изменены.",
            ),
        )
        if (metrics.cpuTemperature >= HOT_CPU_CELSIUS) {
            add(
                AxiomCoreAction(
                    id = REDUCE_LOAD,
                    title = "Снизить нагрузку",
                    detail = "Подготовить безопасную рекомендацию по высокой температуре CPU.",
                    confirmationMessage = "Запустить симуляцию рекомендации? Процессы не будут завершены.",
                ),
            )
        }
        if (metrics.freeStorageGigabytes < LOW_STORAGE_GIGABYTES) {
            add(
                AxiomCoreAction(
                    id = REVIEW_STORAGE,
                    title = "Проверить хранилище",
                    detail = "Получить безопасные рекомендации по освобождению места.",
                    confirmationMessage = "Подготовить список для ручной проверки. Файлы автоматически не удаляются.",
                ),
            )
        }
        if (metrics.batteryHealthPercent < LOW_BATTERY_HEALTH_PERCENT) {
            add(
                AxiomCoreAction(
                    id = BATTERY_CARE,
                    title = "Рекомендации по батарее",
                    detail = "Подготовить шаги для проверки состояния аккумулятора.",
                    confirmationMessage = "Показать рекомендации. Параметры питания не будут изменены.",
                ),
            )
        }
    }

    private fun moodFor(metrics: DeviceMetrics): String = when {
        metrics.cpuTemperature >= HOT_CPU_CELSIUS -> "anxious"
        metrics.batteryHealthPercent < LOW_BATTERY_HEALTH_PERCENT ||
            metrics.freeStorageGigabytes < LOW_STORAGE_GIGABYTES -> "sad"
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