package com.apple.axiom.simulator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceMetricsSimulatorTest {
    private val simulator = DeviceMetricsSimulator()

    @Test
    fun generatesExpectedDeviceStates() {
        val scenarios = simulator.generateScenarios()

        assertEquals(
            listOf("normal", "cpu_overheating", "battery_wear", "low_storage"),
            scenarios.map { it.name },
        )
        assertTrue(scenarios.first { it.name == "cpu_overheating" }.metrics.cpuTemperature > 90.0)
        assertTrue(scenarios.first { it.name == "battery_wear" }.metrics.batteryHealthPercent < 50)
    }

    @Test
    fun serializesScenarioAsSystemLogJson() {
        val json = Json.parseToJsonElement(simulator.toSystemLog(simulator.generateScenarios().first()))
            .jsonObject

        assertEquals("normal", json.getValue("name").jsonPrimitive.content)
        assertEquals(42.0, json.getValue("metrics").jsonObject.getValue("cpuTemperature").jsonPrimitive.content.toDouble())
    }
}