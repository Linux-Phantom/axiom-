package com.apple.axiom.platform

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxMetricsCollectorTest {
    @Test
    fun readsHwmonBatteryProcfsAndStorageMetrics() {
        val root = Files.createTempDirectory("axiom-linux-metrics").toFile()
        try {
            val sysRoot = File(root, "sys")
            val procRoot = File(root, "proc")
            val storageRoot = File(root, "storage")
            val hwmon = File(sysRoot, "class/hwmon/hwmon0").apply { mkdirs() }
            File(hwmon, "temp1_input").writeText("92500\n")
            File(hwmon, "fan1_input").writeText("3100\n")

            val battery = File(sysRoot, "class/power_supply/BAT0").apply { mkdirs() }
            File(battery, "type").writeText("Battery\n")
            File(battery, "energy_full").writeText("45000000\n")
            File(battery, "energy_full_design").writeText("50000000\n")

            procRoot.mkdirs()
            File(procRoot, "stat").writeText("cpu 100 0 50 850 0 0 0 0\n")
            File(procRoot, "meminfo").writeText("MemTotal: 1048576 kB\nMemAvailable: 524288 kB\n")
            storageRoot.mkdirs()

            val snapshot = LinuxMetricsCollector(
                sysRoot = sysRoot,
                procRoot = procRoot,
                storageRoot = storageRoot,
                cpuSamplingIntervalMillis = 0,
            ).collect()

            assertEquals(92.5, snapshot.deviceMetrics.cpuTemperature)
            assertEquals(90, snapshot.deviceMetrics.batteryHealthPercent)
            assertEquals(3_100, snapshot.deviceMetrics.fanRpm)
            assertEquals(1_073_741_824L, snapshot.memoryTotalBytes)
            assertEquals(536_870_912L, snapshot.memoryAvailableBytes)
            assertEquals(50.0, snapshot.memoryUsagePercent)
            assertNull(snapshot.cpuUsagePercent)
        } finally {
            root.deleteRecursively()
        }
    }
}