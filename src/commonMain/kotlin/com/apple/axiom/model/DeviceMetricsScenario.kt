package com.apple.axiom.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceMetricsScenario(
    val name: String,
    val description: String,
    val metrics: DeviceMetrics,
)