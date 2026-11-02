package com.apple.axiom.platform

import com.apple.axiom.model.DeviceMetrics
import kotlinx.serialization.Serializable

@Serializable
data class LinuxSystemSnapshot(
    val deviceMetrics: DeviceMetrics,
    val cpuUsagePercent: Double?,
    val memoryTotalBytes: Long?,
    val memoryAvailableBytes: Long?,
    val memoryUsagePercent: Double?,
    val collectedAtEpochMillis: Long,
)