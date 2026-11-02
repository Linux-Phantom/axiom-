package com.apple.axiom.platform

import java.io.File

enum class LinuxFixRisk {
    READ_ONLY,
    REVERSIBLE,
    DESTRUCTIVE,
}

data class LinuxFixerAction(
    val id: String,
    val title: String,
    val reason: String,
    val commandPreview: String,
    val risk: LinuxFixRisk,
    val requiresElevation: Boolean,
    val manualConfirmationRequired: Boolean = true,
)

data class LinuxFixerPlan(
    val agentMessage: String,
    val actions: List<LinuxFixerAction>,
    val executionNotice: String = "Команды показаны для проверки и не выполняются приложением.",
)

class LinuxFixerPlanner(
    private val commandExists: (String) -> Boolean = ::commandExistsOnPath,
) {
    fun plan(snapshot: LinuxSystemSnapshot, agentMessage: String): LinuxFixerPlan {
        val actions = buildList {
            if ((snapshot.cpuUsagePercent ?: 0.0) >= HIGH_CPU_PERCENT) {
                add(
                    LinuxFixerAction(
                        id = "inspect-cpu-processes",
                        title = "Проверить процессы с высокой нагрузкой",
                        reason = "Сначала определить процесс; ничего не завершать автоматически.",
                        commandPreview = "ps -eo pid,comm,%cpu --sort=-%cpu | head -n 10",
                        risk = LinuxFixRisk.READ_ONLY,
                        requiresElevation = false,
                    ),
                )
                add(
                    LinuxFixerAction(
                        id = "request-process-stop",
                        title = "Подготовить остановку зависшего процесса",
                        reason = "Проверьте PID и сохраните данные. Отправка SIGTERM может закрыть приложение.",
                        commandPreview = "kill -TERM <проверенный-PID>",
                        risk = LinuxFixRisk.DESTRUCTIVE,
                        requiresElevation = false,
                    ),
                )
            }

            if (commandExists("powerprofilesctl")) {
                add(
                    LinuxFixerAction(
                        id = "power-saver-profile",
                        title = "Предложить профиль энергосбережения",
                        reason = "Переключение может снизить производительность и обычно обратимо.",
                        commandPreview = "powerprofilesctl set power-saver",
                        risk = LinuxFixRisk.REVERSIBLE,
                        requiresElevation = false,
                    ),
                )
            }

            when {
                snapshot.deviceMetrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES && commandExists("apt") -> add(
                    LinuxFixerAction(
                        id = "apt-cache-clean",
                        title = "Предложить очистку кэша APT",
                        reason = "Удаляет загруженные архивы пакетов; установленные пакеты не удаляются.",
                        commandPreview = "sudo apt clean",
                        risk = LinuxFixRisk.DESTRUCTIVE,
                        requiresElevation = true,
                    ),
                )
                snapshot.deviceMetrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES && commandExists("dnf") -> add(
                    LinuxFixerAction(
                        id = "dnf-cache-clean",
                        title = "Предложить очистку кэша DNF",
                        reason = "Удаляет кэшированные метаданные/пакеты и может потребовать их повторной загрузки.",
                        commandPreview = "sudo dnf clean packages",
                        risk = LinuxFixRisk.DESTRUCTIVE,
                        requiresElevation = true,
                    ),
                )
            }

            if (snapshot.deviceMetrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES && commandExists("systemd-tmpfiles")) {
                add(
                    LinuxFixerAction(
                        id = "tmpfiles-clean-preview",
                        title = "Проверить правила очистки временных файлов",
                        reason = "Выполняет только системные правила tmpfiles; проверьте настройки дистрибутива.",
                        commandPreview = "systemd-tmpfiles --clean",
                        risk = LinuxFixRisk.DESTRUCTIVE,
                        requiresElevation = true,
                    ),
                )
            }

            if (snapshot.deviceMetrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES && commandExists("journalctl")) {
                add(
                    LinuxFixerAction(
                        id = "journald-vacuum-preview",
                        title = "Проверить размер журнала systemd",
                        reason = "Сначала проверьте размер. Vacuum удаляет старые записи и может затронуть диагностику.",
                        commandPreview = "journalctl --disk-usage\n# Только после отдельного подтверждения: sudo journalctl --vacuum-time=14d",
                        risk = LinuxFixRisk.DESTRUCTIVE,
                        requiresElevation = true,
                    ),
                )
            }
        }

        return LinuxFixerPlan(agentMessage, actions)
    }

    private companion object {
        const val HIGH_CPU_PERCENT = 85.0
        const val LOW_STORAGE_GIGABYTES = 8.0
    }
}

private fun commandExistsOnPath(command: String): Boolean =
    System.getenv("PATH").orEmpty().split(File.pathSeparator).any { directory ->
        File(directory, command).let { it.isFile && it.canExecute() }
    }