import SwiftUI

#if os(iOS)
import WidgetKit
#endif

@MainActor
final class AxiomStateStore: ObservableObject {
    @Published private(set) var snapshot: AxiomSnapshot
    @Published private(set) var pendingAction: AxiomFixAction?
    @Published private(set) var notice: String?
    @Published private(set) var errorMessage: String?
    @Published private(set) var isAnalyzing = false

    private let defaults: UserDefaults
    private let core: AxiomCoreServicing?
    private var metricsTask: Task<Void, Never>?
    private var metricsFeedToken: UUID?
    private var subscribedMetricsFeed: AxiomMetricsFeed?

    init(core: AxiomCoreServicing? = AxiomCoreServiceFactory.make()) {
        self.core = core
        defaults = UserDefaults(suiteName: AxiomSharedStorage.appGroupIdentifier) ?? .standard
        if let data = defaults.data(forKey: AxiomSharedStorage.snapshotKey),
           let restored = try? JSONDecoder().decode(AxiomSnapshot.self, from: data) {
            snapshot = restored
        } else {
            snapshot = .preview
        }
        pendingAction = nil
        notice = nil
        errorMessage = nil
    }

    func update(snapshot: AxiomSnapshot) {
        self.snapshot = snapshot
        persistSnapshot()
    }

    func analyzeCurrentMetrics() {
        updateMetrics(snapshot.metrics)
    }

    func updateMetrics(_ metrics: AxiomDeviceMetrics) {
        snapshot.metrics = metrics
        snapshot.updatedAt = .now
        persistSnapshot()

        guard let core else {
            errorMessage = AxiomCoreServiceFactory.unavailableMessage
            return
        }

        isAnalyzing = true
        errorMessage = nil
        core.analyze(metrics) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                self.isAnalyzing = false
                switch result {
                case .success(let analysis):
                    self.snapshot.metrics = analysis.metrics
                    self.snapshot.companionReply = AxiomAgentReply(
                        agent: .companion,
                        text: analysis.companionMessage,
                        createdAt: analysis.updatedAt
                    )
                    self.snapshot.fixerReply = AxiomAgentReply(
                        agent: .fixer,
                        text: analysis.fixerMessage,
                        createdAt: analysis.updatedAt
                    )
                    self.snapshot.fixActions = analysis.actions
                    self.snapshot.updatedAt = analysis.updatedAt
                    self.errorMessage = nil
                    self.persistSnapshot()
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    func bindMetrics(_ updates: AsyncStream<AxiomDeviceMetrics>) {
        metricsTask?.cancel()
        metricsTask = Task { [weak self] in
            for await metrics in updates {
                guard !Task.isCancelled else { return }
                self?.updateMetrics(metrics)
            }
        }
    }

    func startMonitoring(from feed: AxiomMetricsFeed = .shared) {
        guard metricsFeedToken == nil else { return }
        subscribedMetricsFeed = feed
        metricsFeedToken = feed.subscribe { [weak self] metrics in
            self?.updateMetrics(metrics)
        }
    }

    func stopMonitoring() {
        metricsTask?.cancel()
        metricsTask = nil
        if let metricsFeedToken {
            subscribedMetricsFeed?.unsubscribe(metricsFeedToken)
            self.metricsFeedToken = nil
            subscribedMetricsFeed = nil
        }
    }

    func consumePendingWidgetAction() {
        guard let actionID = defaults.string(forKey: AxiomSharedStorage.pendingFixActionKey) else { return }
        defaults.removeObject(forKey: AxiomSharedStorage.pendingFixActionKey)
        guard let action = snapshot.fixActions.first(where: { $0.id == actionID }) else {
            notice = "Рекомендация устарела. Запустите диагностику еще раз."
            return
        }
        requestConfirmation(for: action)
    }

    private func persistSnapshot() {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: AxiomSharedStorage.snapshotKey)

        #if os(iOS)
        WidgetCenter.shared.reloadAllTimelines()
        Task {
            await AxiomLiveActivityController.synchronize(with: snapshot)
        }
        #endif
    }

    func requestConfirmation(for action: AxiomFixAction) {
        pendingAction = action
        notice = nil
    }

    func confirmPendingAction() {
        guard let action = pendingAction else { return }
        pendingAction = nil
        defaults.removeObject(forKey: AxiomSharedStorage.pendingFixActionKey)

        guard let core else {
            errorMessage = AxiomCoreServiceFactory.unavailableMessage
            return
        }

        core.performAction(actionID: action.id) { [weak self] result in
            Task { @MainActor in
                guard let self else { return }
                switch result {
                case .success(let outcome):
                    self.notice = outcome.message
                    self.errorMessage = nil
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    func cancelPendingAction() {
        pendingAction = nil
    }
}

struct AxiomFixActionList: View {
    @EnvironmentObject private var store: AxiomStateStore

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if store.isAnalyzing {
                ProgressView("Анализ устройства")
                    .font(.footnote)
            }

            if let errorMessage = store.errorMessage {
                Label(errorMessage, systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }

            ForEach(store.snapshot.fixActions) { action in
                Button {
                    store.requestConfirmation(for: action)
                } label: {
                    Label(action.title, systemImage: action.systemImage)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.bordered)
                .accessibilityHint(action.detail)
            }

            if let notice = store.notice {
                Text(notice)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .confirmationDialog(
            "Подтвердить запрос к Axiom?",
            isPresented: Binding(
                get: { store.pendingAction != nil },
                set: { if !$0 { store.cancelPendingAction() } }
            ),
            titleVisibility: .visible
        ) {
            Button("Передать рекомендацию") {
                store.confirmPendingAction()
            }
            Button("Отмена", role: .cancel) {
                store.cancelPendingAction()
            }
        } message: {
            Text(store.pendingAction?.confirmationMessage ?? "")
        }
    }
}