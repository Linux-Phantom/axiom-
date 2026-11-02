package com.apple.axiom.model

import kotlinx.serialization.Serializable

@Serializable
data class AxiomCoreAction(
    val id: String,
    val title: String,
    val detail: String,
    val confirmationMessage: String,
)

@Serializable
data class AxiomCoreUpdate(
    val metrics: DeviceMetrics,
    val mood: String,
    val companionMessage: String,
    val fixerMessage: String,
    val actions: List<AxiomCoreAction>,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class AxiomCoreActionResult(
    val actionId: String,
    val message: String,
    val simulated: Boolean,
)