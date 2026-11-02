package com.apple.axiom.platform

object LinuxMetricsProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val snapshot = LinuxMetricsCollector().collect()
        println("CPU temperature: ${snapshot.deviceMetrics.cpuTemperature.asAvailable("°C")}")
        println("CPU usage: ${snapshot.cpuUsagePercent.asAvailable("%")}")
        println("Fan speed: ${snapshot.deviceMetrics.fanRpm.asAvailable("RPM")}")
        println("Battery health: ${snapshot.deviceMetrics.batteryHealthPercent.asAvailable("%")}")
        println("Free storage: ${snapshot.deviceMetrics.freeStorageGigabytes.asAvailable("GB")}")
        println("Memory total: ${snapshot.memoryTotalBytes.asBytes()}")
        println("Memory available: ${snapshot.memoryAvailableBytes.asBytes()}")
        println("Memory usage: ${snapshot.memoryUsagePercent.asAvailable("%")}")
    }
}

private fun Double.asAvailable(unit: String): String = if (this < 0) "unavailable" else "%.1f %s".format(this, unit)
private fun Int.asAvailable(unit: String): String = if (this < 0) "unavailable" else "$this $unit"
private fun Double?.asAvailable(unit: String): String = this?.let { it.asAvailable(unit) } ?: "unavailable"
private fun Long?.asBytes(): String = this?.let { "$it bytes" } ?: "unavailable"