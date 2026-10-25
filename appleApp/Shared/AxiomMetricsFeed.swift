import Foundation

@MainActor
final class AxiomMetricsFeed {
    static let shared = AxiomMetricsFeed()

    private var observers: [UUID: (AxiomDeviceMetrics) -> Void] = [:]

    @discardableResult
    func subscribe(_ observer: @escaping (AxiomDeviceMetrics) -> Void) -> UUID {
        let id = UUID()
        observers[id] = observer
        return id
    }

    func unsubscribe(_ id: UUID) {
        observers.removeValue(forKey: id)
    }

    func publish(_ metrics: AxiomDeviceMetrics) {
        observers.values.forEach { $0(metrics) }
    }
}