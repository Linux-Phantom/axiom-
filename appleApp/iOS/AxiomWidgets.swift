import ActivityKit
import SwiftUI
import WidgetKit

@main
struct AxiomWidgetBundle: WidgetBundle {
    var body: some Widget {
        AxiomStatusWidget()
        AxiomLiveActivityWidget()
    }
}

struct AxiomWidgetEntry: TimelineEntry {
    var date: Date
    var snapshot: AxiomSnapshot
}

struct AxiomWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> AxiomWidgetEntry {
        AxiomWidgetEntry(date: .now, snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (AxiomWidgetEntry) -> Void) {
        completion(currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AxiomWidgetEntry>) -> Void) {
        let entry = currentEntry()
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(15 * 60))))
    }

    private func currentEntry() -> AxiomWidgetEntry {
        let data = UserDefaults(suiteName: AxiomSharedStorage.appGroupIdentifier)?
            .data(forKey: AxiomSharedStorage.snapshotKey)
        let snapshot = data.flatMap { try? JSONDecoder().decode(AxiomSnapshot.self, from: $0) } ?? .preview
        return AxiomWidgetEntry(date: .now, snapshot: snapshot)
    }
}

struct AxiomStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AxiomStatusWidget", provider: AxiomWidgetProvider()) { entry in
            AxiomWidgetContent(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Состояние Axiom")
        .description("Настроение компаньона и состояние устройства.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

private struct AxiomWidgetContent: View {
    @Environment(\.widgetFamily) private var family
    let snapshot: AxiomSnapshot

    var body: some View {
        Group {
            switch family {
            case .accessoryInline:
                Label(snapshot.mood.title, systemImage: snapshot.mood.symbolName)
            case .accessoryRectangular:
                VStack(alignment: .leading, spacing: 4) {
                    Label(snapshot.mood.title, systemImage: snapshot.mood.symbolName)
                    Text("CPU \(snapshot.metrics.cpuTemperature, specifier: "%.0f")° · Батарея \(snapshot.metrics.batteryHealthPercent)%")
                        .font(.caption2)
                }
            case .systemSmall:
                VStack(alignment: .leading, spacing: 8) {
                    AxiomMascotFace(mood: snapshot.mood, size: 54)
                    Text(snapshot.mood.title)
                        .font(.headline)
                        .lineLimit(1)
                    Text("CPU \(snapshot.metrics.cpuTemperature, specifier: "%.0f")° · \(snapshot.metrics.batteryHealthPercent)%")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            default:
                HStack(spacing: 14) {
                    AxiomMascotFace(mood: snapshot.mood, size: 72)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(snapshot.mood.title)
                            .font(.headline)
                        Text(snapshot.companionReply?.text ?? "Устройство под наблюдением.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                        if let action = snapshot.fixActions.first {
                            Button(intent: RequestAxiomFixIntent(actionID: action.id)) {
                                Label(action.title, systemImage: action.systemImage)
                                    .font(.caption.weight(.medium))
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(family == .accessoryInline ? 0 : 12)
        .containerBackground(.background, for: .widget)
    }
}

struct AxiomActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var mood: AxiomMood
        var title: String
        var message: String
        var updatedAt: Date
    }

    var deviceName: String
}

struct AxiomLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AxiomActivityAttributes.self) { context in
            HStack(spacing: 12) {
                Image(systemName: context.state.mood.symbolName)
                    .font(.title2)
                VStack(alignment: .leading, spacing: 3) {
                    Text(context.state.title).font(.headline)
                    Text(context.state.message).font(.caption).lineLimit(2)
                }
                Spacer(minLength: 0)
                Text(context.attributes.deviceName).font(.caption2)
            }
            .padding()
            .activityBackgroundTint(Color(red: 0.11, green: 0.15, blue: 0.18))
            .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.state.mood.symbolName)
                        .accessibilityLabel(context.state.mood.title)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.attributes.deviceName)
                        .font(.caption2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(context.state.title).font(.headline)
                        Text(context.state.message).font(.caption).lineLimit(2)
                    }
                }
            } compactLeading: {
                Image(systemName: context.state.mood.symbolName)
            } compactTrailing: {
                Text(context.state.mood == .anxious ? "Жарко" : "Внимание")
                    .font(.caption2)
            } minimal: {
                Image(systemName: context.state.mood.symbolName)
            }
            .keylineTint(Color(red: 0.12, green: 0.66, blue: 0.55))
        }
    }
}