import AppIntents
import WidgetKit

struct RequestAxiomFixIntent: AppIntent {
    static var title: LocalizedStringResource = "Запросить рекомендацию Axiom"
    static var openAppWhenRun = false

    @Parameter(title: "Идентификатор действия")
    var actionID: String

    init() {
        actionID = ""
    }

    init(actionID: String) {
        self.actionID = actionID
    }

    func perform() async throws -> some IntentResult {
        let defaults = UserDefaults(suiteName: AxiomSharedStorage.appGroupIdentifier)
        defaults?.set(actionID, forKey: AxiomSharedStorage.pendingFixActionKey)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}