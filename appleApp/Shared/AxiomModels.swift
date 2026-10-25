import Foundation

enum AxiomMood: String, Codable, CaseIterable, Sendable {
    case happy
    case anxious
    case sad

    var title: String {
        switch self {
        case .happy: "Счастливый"
        case .anxious: "Тревожный: перегрев"
        case .sad: "Грустный"
        }
    }

    var symbolName: String {
        switch self {
        case .happy: "sparkles"
        case .anxious: "thermometer.high"
        case .sad: "battery.25percent"
        }
    }
}

enum AxiomAgent: String, Codable, Sendable {
    case companion = "The Companion"
    case fixer = "The Fixer"
}

struct AxiomDeviceMetrics: Codable, Hashable, Sendable {
    var cpuTemperature: Double
    var batteryHealthPercent: Int
    var freeStorageGigabytes: Double
    var fanRpm: Int
}

struct AxiomAgentReply: Codable, Hashable, Identifiable, Sendable {
    var id: UUID
    var agent: AxiomAgent
    var text: String
    var createdAt: Date

    init(id: UUID = UUID(), agent: AxiomAgent, text: String, createdAt: Date = .now) {
        self.id = id
        self.agent = agent
        self.text = text
        self.createdAt = createdAt
    }
}

struct AxiomFixAction: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var title: String
    var detail: String
    var systemImage: String
    var confirmationMessage: String
}

struct AxiomSnapshot: Codable, Hashable, Sendable {
    var deviceName: String
    var metrics: AxiomDeviceMetrics
    var companionReply: AxiomAgentReply?
    var fixerReply: AxiomAgentReply? = nil
    var fixActions: [AxiomFixAction]
    var updatedAt: Date

    var mood: AxiomMood {
        if metrics.cpuTemperature >= 85 {
            return .anxious
        }
        if metrics.batteryHealthPercent < 70 || metrics.freeStorageGigabytes < 8 {
            return .sad
        }
        return .happy
    }

    static let preview = AxiomSnapshot(
        deviceName: "Этот Mac",
        metrics: AxiomDeviceMetrics(
            cpuTemperature: 42,
            batteryHealthPercent: 96,
            freeStorageGigabytes: 128,
            fanRpm: 1_200
        ),
        companionReply: AxiomAgentReply(
            agent: .companion,
            text: "Я в отличной форме. Вентилятор почти не шумит, можно спокойно работать."
        ),
        fixActions: [
            AxiomFixAction(
                id: "reduce-load",
                title: "Снизить нагрузку",
                detail: "Найти процессы с высокой нагрузкой на CPU",
                systemImage: "gauge.with.dots.needle.50percent",
                confirmationMessage: "Axiom подготовит рекомендацию. Системные процессы не будут завершены автоматически."
            ),
            AxiomFixAction(
                id: "review-storage",
                title: "Проверить хранилище",
                detail: "Подготовить безопасные варианты освобождения места",
                systemImage: "externaldrive",
                confirmationMessage: "Axiom подготовит список файлов для проверки. Удаление потребует отдельного подтверждения."
            ),
        ],
        updatedAt: .now
    )
}

enum AxiomSharedStorage {
    static let appGroupIdentifier = "group.com.apple.axiom"
    static let snapshotKey = "axiom.snapshot.v1"
    static let pendingFixActionKey = "axiom.pending-fix-action.v1"
}