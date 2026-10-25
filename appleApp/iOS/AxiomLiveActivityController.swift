import ActivityKit
import Foundation

@MainActor
enum AxiomLiveActivityController {
    static func synchronize(with snapshot: AxiomSnapshot) async {
        let activities = Activity<AxiomActivityAttributes>.activities

        guard snapshot.mood != .happy,
              ActivityAuthorizationInfo().areActivitiesEnabled else {
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            return
        }

        let state = AxiomActivityAttributes.ContentState(
            mood: snapshot.mood,
            title: snapshot.mood.title,
            message: snapshot.companionReply?.text ?? "Нужна проверка состояния устройства.",
            updatedAt: snapshot.updatedAt
        )
        let content = ActivityContent(state: state, staleDate: .now.addingTimeInterval(15 * 60))

        if let activity = activities.first {
            await activity.update(content)
            return
        }

        do {
            _ = try Activity.request(
                attributes: AxiomActivityAttributes(deviceName: snapshot.deviceName),
                content: content,
                pushType: nil
            )
        } catch {
            // Live Activities can be disabled by the user or unavailable for this app.
        }
    }
}