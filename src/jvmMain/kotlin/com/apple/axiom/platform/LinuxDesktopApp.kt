package com.apple.axiom.platform

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

object LinuxDesktopApp {
    @JvmStatic
    fun main(args: Array<String>) {
        check(System.getProperty("os.name").lowercase().contains("linux")) {
            "Axiom Linux Desktop can only run on Linux"
        }
        SwingUtilities.invokeLater { LinuxDesktopShell().start() }
    }
}

private class LinuxDesktopShell(
    private val collector: LinuxMetricsCollector = LinuxMetricsCollector(),
) {
    private val background: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private var window: JFrame? = null
    private var trayIcon: TrayIcon? = null
    private var currentSnapshot: LinuxSystemSnapshot? = null
    private var latestAgentResult: LinuxAgentResult? = null
    private var agentService: LinuxAgentService? = null
    private var agentServiceError: String? = null

    private lateinit var moodIcon: MascotIcon
    private lateinit var moodLabel: JLabel
    private lateinit var cpuLabel: JLabel
    private lateinit var memoryLabel: JLabel
    private lateinit var hardwareLabel: JLabel
    private lateinit var companionLabel: JLabel
    private lateinit var fixerArea: JTextArea
    private lateinit var actionPanel: JPanel
    private lateinit var progressBar: JProgressBar
    private lateinit var diagnoseButton: JButton

    fun start() {
        createWindow()
        installTray()
        window?.isVisible = true
        refreshMetrics()
        background.scheduleAtFixedRate(
            { refreshMetrics() },
            METRICS_REFRESH_SECONDS,
            METRICS_REFRESH_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun createWindow() {
        moodIcon = MascotIcon()
        moodLabel = JLabel("Ожидание метрик")
        cpuLabel = JLabel("CPU: —")
        memoryLabel = JLabel("Память: —")
        hardwareLabel = JLabel("Температура / батарея / диск / вентилятор: —")
        companionLabel = JLabel("The Companion появится после диагностики.")
        companionLabel.alignmentX = JComponent.LEFT_ALIGNMENT
        companionLabel.font = companionLabel.font.deriveFont(Font.PLAIN, 13f)

        fixerArea = JTextArea("Действия The Fixer появятся после диагностики.")
        fixerArea.isEditable = false
        fixerArea.lineWrap = true
        fixerArea.wrapStyleWord = true
        fixerArea.background = Color(0, 0, 0, 0)
        fixerArea.rows = 5

        actionPanel = JPanel()
        actionPanel.layout = BoxLayout(actionPanel, BoxLayout.Y_AXIS)
        actionPanel.alignmentX = JComponent.LEFT_ALIGNMENT

        progressBar = JProgressBar()
        progressBar.isIndeterminate = true
        progressBar.isVisible = false

        diagnoseButton = JButton("Запустить диагностику")
        diagnoseButton.alignmentX = JComponent.LEFT_ALIGNMENT
        diagnoseButton.addActionListener { runDiagnosis() }

        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        content.add(moodIcon)
        content.add(Box.createVerticalStrut(8))
        content.add(moodLabel)
        content.add(Box.createVerticalStrut(12))
        content.add(cpuLabel)
        content.add(memoryLabel)
        content.add(hardwareLabel)
        content.add(Box.createVerticalStrut(12))
        content.add(sectionLabel("The Companion"))
        content.add(companionLabel)
        content.add(Box.createVerticalStrut(12))
        content.add(sectionLabel("Self-Healing: просмотр, не автозапуск"))
        content.add(JScrollPane(fixerArea).apply {
            preferredSize = Dimension(340, 90)
            maximumSize = Dimension(Int.MAX_VALUE, 110)
            alignmentX = JComponent.LEFT_ALIGNMENT
        })
        content.add(Box.createVerticalStrut(6))
        content.add(actionPanel)
        content.add(Box.createVerticalStrut(8))
        content.add(progressBar)
        content.add(diagnoseButton)

        window = JFrame().apply {
            title = "Axiom"
            type = java.awt.Window.Type.UTILITY
            isAlwaysOnTop = true
            isResizable = false
            defaultCloseOperation = JFrame.HIDE_ON_CLOSE
            contentPane = content
            pack()
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            setLocation(screen.width - width - WINDOW_MARGIN, screen.height - height - WINDOW_MARGIN)
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(event: WindowEvent?) {
                    close()
                }
            })
        }
    }

    private fun installTray() {
        if (!SystemTray.isSupported()) return

        val menu = PopupMenu().apply {
            add(MenuItem("Открыть питомца").apply {
                addActionListener { showWindow() }
            })
            add(MenuItem("Обновить метрики").apply {
                addActionListener { refreshMetrics() }
            })
            add(MenuItem("Запустить диагностику").apply {
                addActionListener { runDiagnosis() }
            })
            addSeparator()
            add(MenuItem("Выход").apply {
                addActionListener { close() }
            })
        }

        trayIcon = TrayIcon(createTrayImage(Mood.HAPPY), "Axiom", menu).apply {
            isImageAutoSize = true
            addActionListener { showWindow() }
        }
        runCatching { SystemTray.getSystemTray().add(trayIcon) }
            .onFailure { trayIcon = null }
    }

    private fun refreshMetrics() {
        background.execute {
            runCatching { collector.collect() }
                .onSuccess { snapshot ->
                    SwingUtilities.invokeLater {
                        currentSnapshot = snapshot
                        render(snapshot)
                    }
                }
                .onFailure { error ->
                    SwingUtilities.invokeLater {
                        moodLabel.text = "Датчики недоступны"
                        moodLabel.toolTipText = error.message
                        updateTray(Mood.SAD)
                    }
                }
        }
    }

    private fun render(snapshot: LinuxSystemSnapshot) {
        val mood = moodOf(snapshot)
        moodIcon.mood = mood
        moodLabel.text = "${mood.title} · ${snapshot.collectedAtEpochMillis.let { java.time.Instant.ofEpochMilli(it).toString() }}"
        cpuLabel.text = "CPU: ${snapshot.cpuUsagePercent.formatPercent()}"
        memoryLabel.text = "Память: ${snapshot.memoryUsagePercent.formatPercent()} (${snapshot.memoryAvailableBytes.formatBytes()} свободно)"
        hardwareLabel.text = listOf(
            "${snapshot.deviceMetrics.cpuTemperature.formatTemperature()}",
            "${snapshot.deviceMetrics.batteryHealthPercent.formatPercent()}",
            "${snapshot.deviceMetrics.freeStorageGigabytes.formatStorage()}",
            "${snapshot.deviceMetrics.fanRpm.formatRpm()}",
        ).joinToString(" · ")
        updateTray(mood)
        window?.title = "Axiom · ${mood.title}"
        window?.repaint()
    }

    private fun runDiagnosis() {
        val snapshot = currentSnapshot ?: run {
            companionLabel.text = "Сначала дождитесь чтения системных метрик."
            return
        }
        diagnoseButton.isEnabled = false
        progressBar.isVisible = true

        background.execute {
            val result = runCatching {
                val service = agentService ?: LinuxAgentServiceFactory.create().also { agentService = it }
                service.analyze(snapshot)
            }
            SwingUtilities.invokeLater {
                diagnoseButton.isEnabled = true
                progressBar.isVisible = false
                result.onSuccess(::renderAgentResult).onFailure { error ->
                    companionLabel.text = "Не удалось выполнить диагностику: ${error.message ?: "ошибка API"}"
                }
            }
        }
    }

    private fun renderAgentResult(result: LinuxAgentResult) {
        latestAgentResult = result
        companionLabel.text = "${result.companionMessage}\n\nДиагноз: ${result.medicMessage}"
        fixerArea.text = result.fixerPlan.agentMessage + "\n\n" + result.fixerPlan.executionNotice
        actionPanel.removeAll()

        result.fixerPlan.actions.forEach { action ->
            val button = JButton("${action.title} · ${action.risk}")
            button.alignmentX = JComponent.LEFT_ALIGNMENT
            button.addActionListener { previewAction(action) }
            actionPanel.add(button)
            actionPanel.add(Box.createVerticalStrut(4))
        }
        actionPanel.revalidate()
        actionPanel.repaint()
        window?.pack()
    }

    private fun previewAction(action: LinuxFixerAction) {
        val risk = when (action.risk) {
            LinuxFixRisk.READ_ONLY -> "Только чтение"
            LinuxFixRisk.REVERSIBLE -> "Изменение настроек; можно вернуть обратно"
            LinuxFixRisk.DESTRUCTIVE -> "Изменяет или удаляет системные данные"
        }
        val details = "${action.reason}\n\nРиск: $risk\nТребуются права администратора: ${if (action.requiresElevation) "да" else "нет"}\n\nКоманда для ручного просмотра:\n${action.commandPreview}\n\nAxiom не выполняет эту команду автоматически."
        JOptionPane.showMessageDialog(window, details, action.title, JOptionPane.WARNING_MESSAGE)
    }

    private fun updateTray(mood: Mood) {
        trayIcon?.image = createTrayImage(mood)
        trayIcon?.toolTip = "Axiom · ${mood.title}"
    }

    private fun showWindow() {
        SwingUtilities.invokeLater {
            window?.isVisible = true
            window?.toFront()
        }
    }

    private fun close() {
        background.shutdownNow()
        runCatching { agentService?.close() }
        trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        window?.dispose()
        window = null
    }

    private fun sectionLabel(text: String) = JLabel(text).apply {
        alignmentX = JComponent.LEFT_ALIGNMENT
        font = font.deriveFont(Font.BOLD, 12f)
    }

    private enum class Mood(val title: String, val color: Color) {
        HAPPY("Счастливый", Color(33, 150, 126)),
        ANXIOUS("Тревожный · перегрев", Color(221, 88, 52)),
        SAD("Грустный", Color(79, 111, 168)),
    }

    private class MascotIcon : JComponent() {
        var mood: Mood = Mood.HAPPY
            set(value) {
                field = value
                repaint()
            }

        init {
            preferredSize = Dimension(72, 72)
            maximumSize = preferredSize
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val inset = 6
            g.color = mood.color
            g.fillRoundRect(inset, inset, width - inset * 2, height - inset * 2, 24, 24)
            g.color = Color.WHITE
            g.fillOval(width / 2 - 15, height / 2 - 7, 6, 8)
            g.fillOval(width / 2 + 9, height / 2 - 7, 6, 8)
            g.color = Color(30, 40, 45)
            g.stroke = java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
            val mouthY = height / 2 + 11
            when (mood) {
                Mood.HAPPY -> g.drawArc(width / 2 - 8, mouthY - 5, 16, 10, 200, 140)
                Mood.ANXIOUS -> g.drawOval(width / 2 - 4, mouthY - 3, 8, 8)
                Mood.SAD -> g.drawArc(width / 2 - 8, mouthY - 1, 16, 10, 20, 140)
            }
            g.dispose()
        }
    }

    private fun moodOf(snapshot: LinuxSystemSnapshot): Mood = when {
        snapshot.deviceMetrics.cpuTemperature >= HOT_CPU_CELSIUS ||
            (snapshot.cpuUsagePercent ?: 0.0) >= HIGH_CPU_PERCENT -> Mood.ANXIOUS
        snapshot.deviceMetrics.batteryHealthPercent in 0 until LOW_BATTERY_HEALTH ||
            snapshot.deviceMetrics.freeStorageGigabytes in 0.0..<LOW_STORAGE_GIGABYTES -> Mood.SAD
        else -> Mood.HAPPY
    }

    private fun createTrayImage(mood: Mood): Image {
        val image = BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = mood.color
        g.fillRoundRect(1, 1, TRAY_ICON_SIZE - 2, TRAY_ICON_SIZE - 2, 10, 10)
        g.color = Color.WHITE
        g.fillOval(9, 10, 3, 4)
        g.fillOval(20, 10, 3, 4)
        g.color = Color(30, 40, 45)
        g.drawArc(11, 16, 10, 7, if (mood == Mood.HAPPY) 200 else 20, 140)
        g.dispose()
        return image
    }

    private companion object {
        const val METRICS_REFRESH_SECONDS = 15L
        const val WINDOW_MARGIN = 24
        const val TRAY_ICON_SIZE = 32
        const val HOT_CPU_CELSIUS = 85.0
        const val HIGH_CPU_PERCENT = 85.0
        const val LOW_STORAGE_GIGABYTES = 8.0
        const val LOW_BATTERY_HEALTH = 70
    }
}

private fun Double?.formatPercent(): String = this?.let { "%.0f%%".format(it) } ?: "Н/Д"
private fun Long?.formatBytes(): String = this?.let { "%.1f ГБ".format(it / 1_000_000_000.0) } ?: "Н/Д"
private fun Double.formatTemperature(): String = if (this < 0) "Температура Н/Д" else "CPU %.1f°C".format(this)
private fun Int.formatPercent(): String = if (this < 0) "Батарея Н/Д" else "Батарея $this%"
private fun Double.formatStorage(): String = if (this < 0) "Диск Н/Д" else "Свободно %.1f ГБ".format(this)
private fun Int.formatRpm(): String = if (this < 0) "Вентилятор Н/Д" else "Вентилятор $this RPM"