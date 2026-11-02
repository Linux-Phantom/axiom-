package com.apple.axiom.platform

import com.apple.axiom.model.DeviceMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxFixerPlannerTest {
    @Test
    fun suggestsAllowlistedActionsWithoutEnablingExecution() {
        val planner = LinuxFixerPlanner { command -> command in setOf("apt", "journalctl") }
        val snapshot = LinuxSystemSnapshot(
            deviceMetrics = DeviceMetrics(
                cpuTemperature = 92.0,
                batteryHealthPercent = 88,
                freeStorageGigabytes = 2.0,
                fanRpm = 4_500,
            ),
            cpuUsagePercent = 94.0,
            memoryTotalBytes = 8_000,
            memoryAvailableBytes = 1_000,
            memoryUsagePercent = 87.5,
            collectedAtEpochMillis = 1L,
        )

        val plan = planner.plan(snapshot, "Technical diagnosis")

        assertTrue(plan.actions.any { it.id == "apt-cache-clean" && it.requiresElevation })
        assertTrue(plan.actions.any { it.id == "journald-vacuum-preview" && it.risk == LinuxFixRisk.DESTRUCTIVE })
        assertTrue(plan.actions.any { it.id == "request-process-stop" && it.commandPreview.contains("<проверенный-PID>") })
        assertTrue(plan.actions.all { it.manualConfirmationRequired })
        assertEquals("Technical diagnosis", plan.agentMessage)
        assertTrue(plan.executionNotice.contains("не выполняются"))
    }
}