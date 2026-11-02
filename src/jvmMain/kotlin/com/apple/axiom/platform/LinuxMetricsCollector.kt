package com.apple.axiom.platform

import com.apple.axiom.model.DeviceMetrics
import java.io.File

class LinuxMetricsCollector(
    private val sysRoot: File = File("/sys"),
    private val procRoot: File = File("/proc"),
    private val storageRoot: File = File("/"),
    private val cpuSamplingIntervalMillis: Long = 100,
) {
    fun collect(): LinuxSystemSnapshot {
        check(System.getProperty("os.name").lowercase().contains("linux")) {
            "LinuxMetricsCollector can only read Linux sysfs/procfs"
        }

        val cpuBefore = readCpuCounters()
        if (cpuSamplingIntervalMillis > 0) Thread.sleep(cpuSamplingIntervalMillis)
        val cpuAfter = readCpuCounters()
        val memory = readMemory()
        val memoryUsed = if (memory.totalBytes != null && memory.availableBytes != null && memory.totalBytes > 0) {
            ((memory.totalBytes - memory.availableBytes).coerceAtLeast(0) * 100.0 / memory.totalBytes)
        } else {
            null
        }

        return LinuxSystemSnapshot(
            deviceMetrics = DeviceMetrics(
                cpuTemperature = readCpuTemperature() ?: UNAVAILABLE_TEMPERATURE,
                batteryHealthPercent = readBatteryHealthPercent() ?: UNAVAILABLE_PERCENT,
                freeStorageGigabytes = readFreeStorageGigabytes() ?: UNAVAILABLE_STORAGE,
                fanRpm = readFanRpm() ?: UNAVAILABLE_RPM,
            ),
            cpuUsagePercent = cpuUsage(cpuBefore, cpuAfter),
            memoryTotalBytes = memory.totalBytes,
            memoryAvailableBytes = memory.availableBytes,
            memoryUsagePercent = memoryUsed,
            collectedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun readCpuTemperature(): Double? {
        val hwmonDirectories = sysRoot.resolve("class/hwmon").listFiles().orEmpty()
        val cpuSensors = hwmonDirectories.filter { directory ->
            directory.resolve("name").readTextOrNull()?.trim()?.lowercase() in CPU_HWMON_NAMES
        }
        val selectedDirectories = if (cpuSensors.isNotEmpty()) cpuSensors else hwmonDirectories.toList()
        return selectedDirectories
            .asSequence()
            .flatMap { it.listFiles().orEmpty().asSequence() }
            .filter { it.name.matches(Regex("temp\\d+_input")) }
            .mapNotNull { it.readLongOrNull()?.div(1_000.0) }
            .filter { it in MIN_PLAUSIBLE_TEMPERATURE..MAX_PLAUSIBLE_TEMPERATURE }
            .maxOrNull()
    }

    private fun readFanRpm(): Int? = sysRoot.resolve("class/hwmon")
        .listFiles()
        .orEmpty()
        .asSequence()
        .flatMap { it.listFiles().orEmpty().asSequence() }
        .filter { it.name.matches(Regex("fan\\d+_input")) }
        .mapNotNull { it.readLongOrNull()?.takeIf { rpm -> rpm >= 0 } }
        .maxOrNull()
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()

    private fun readBatteryHealthPercent(): Int? {
        val batteries = sysRoot.resolve("class/power_supply").listFiles().orEmpty()
            .filter { it.resolve("type").readTextOrNull()?.trim() == "Battery" }

        return batteries.firstNotNullOfOrNull { battery ->
            val full = battery.readFirstLong("energy_full", "charge_full")
            val design = battery.readFirstLong("energy_full_design", "charge_full_design")
            if (full != null && design != null && design > 0) {
                ((full * 100.0 / design).toInt()).coerceIn(0, 100)
            } else {
                null
            }
        }
    }

    private fun readFreeStorageGigabytes(): Double? = runCatching {
        storageRoot.usableSpace / DECIMAL_GIGABYTE
    }.getOrNull()

    private fun readMemory(): MemoryInfo {
        val values = procRoot.resolve("meminfo").readLinesOrEmpty().mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 3)
            val value = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            parts.first().removeSuffix(":") to value * KIBIBYTE
        }.toMap()

        return MemoryInfo(values["MemTotal"], values["MemAvailable"])
    }

    private fun readCpuCounters(): CpuCounters? {
        val fields = procRoot.resolve("stat").readLinesOrEmpty().firstOrNull()
            ?.takeIf { it.startsWith("cpu ") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.drop(1)
            ?.mapNotNull(String::toLongOrNull)
            ?: return null
        if (fields.size < 4) return null

        val total = fields.take(STANDARD_CPU_COUNTERS).sum()
        val idle = fields.getOrElse(3) { 0L } + fields.getOrElse(4) { 0L }
        return CpuCounters(total = total, idle = idle)
    }

    private fun cpuUsage(before: CpuCounters?, after: CpuCounters?): Double? {
        if (before == null || after == null) return null
        val totalDelta = after.total - before.total
        val idleDelta = after.idle - before.idle
        if (totalDelta <= 0 || idleDelta < 0) return null
        return ((totalDelta - idleDelta).coerceAtLeast(0) * 100.0 / totalDelta).coerceIn(0.0, 100.0)
    }

    private fun File.readFirstLong(vararg names: String): Long? = names.firstNotNullOfOrNull { name ->
        resolve(name).readLongOrNull()
    }

    private fun File.readLongOrNull(): Long? = readTextOrNull()?.trim()?.toLongOrNull()

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private fun File.readLinesOrEmpty(): List<String> = runCatching { readLines() }.getOrDefault(emptyList())

    private data class CpuCounters(val total: Long, val idle: Long)

    private data class MemoryInfo(val totalBytes: Long?, val availableBytes: Long?)

    private companion object {
        const val UNAVAILABLE_TEMPERATURE = -1.0
        const val UNAVAILABLE_PERCENT = -1
        const val UNAVAILABLE_STORAGE = -1.0
        const val UNAVAILABLE_RPM = -1
        const val MIN_PLAUSIBLE_TEMPERATURE = -20.0
        const val MAX_PLAUSIBLE_TEMPERATURE = 150.0
        const val DECIMAL_GIGABYTE = 1_000_000_000.0
        const val KIBIBYTE = 1_024L
        const val STANDARD_CPU_COUNTERS = 8
        val CPU_HWMON_NAMES = setOf("coretemp", "k10temp", "zenpower", "cpu_thermal", "cpu-thermal", "acpitz")
    }
}