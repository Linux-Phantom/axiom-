package com.apple.axiom.simulator

import com.apple.axiom.model.DeviceMetrics
import com.apple.axiom.model.DeviceMetricsScenario
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceMetricsSimulator {
    fun generateScenarios(): List<DeviceMetricsScenario> = listOf(
        DeviceMetricsScenario(
            name = "normal",
            description = "Device operating within normal limits.",
            metrics = DeviceMetrics(
                cpuTemperature = 42.0,
                batteryHealthPercent = 96,
                freeStorageGigabytes = 128.0,
                fanRpm = 1_200,
            ),
        ),
        DeviceMetricsScenario(
            name = "cpu_overheating",
            description = "CPU temperature and fan speed are unusually high.",
            metrics = DeviceMetrics(
                cpuTemperature = 96.5,
                batteryHealthPercent = 87,
                freeStorageGigabytes = 82.0,
                fanRpm = 6_200,
            ),
        ),
        DeviceMetricsScenario(
            name = "battery_wear",
            description = "Battery health is substantially degraded.",
            metrics = DeviceMetrics(
                cpuTemperature = 39.0,
                batteryHealthPercent = 48,
                freeStorageGigabytes = 115.0,
                fanRpm = 900,
            ),
        ),
        DeviceMetricsScenario(
            name = "low_storage",
            description = "Available storage is critically low.",
            metrics = DeviceMetrics(
                cpuTemperature = 47.0,
                batteryHealthPercent = 94,
                freeStorageGigabytes = 3.2,
                fanRpm = 1_900,
            ),
        ),
    )

    fun toSystemLog(scenario: DeviceMetricsScenario): String = Json.encodeToString(scenario)
}