package com.apple.axiom.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceMetrics(
    val cpuTemperature: Double,
    val batteryHealthPercent: Int,
    val freeStorageGigabytes: Double,
    val fanRpm: Int,
)
