import SwiftUI

struct AxiomDashboardView: View {
    @EnvironmentObject private var store: AxiomStateStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(spacing: 18) {
                        AxiomMascotFace(mood: store.snapshot.mood, size: 104)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(store.snapshot.mood.title)
                                .font(.title3.weight(.semibold))
                            Text(store.snapshot.deviceName)
                                .foregroundStyle(.secondary)
                            Text("Обновлено \(store.snapshot.updatedAt.formatted(date: .omitted, time: .shortened))")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }

                    metrics

                    Button {
                        store.analyzeCurrentMetrics()
                    } label: {
                        Label("Запустить диагностику", systemImage: "stethoscope")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(store.isAnalyzing)

                    if let reply = store.snapshot.companionReply {
                        VStack(alignment: .leading, spacing: 8) {
                            Label("The Companion", systemImage: "bubble.left.and.bubble.right")
                                .font(.headline)
                            Text(reply.text)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Рекомендации")
                            .font(.headline)
                        AxiomFixActionList()
                    }
                }
                .padding(20)
            }
            .navigationTitle("Axiom")
            .background(Color(uiColor: .systemGroupedBackground))
            .onAppear {
                store.startMonitoring()
                store.consumePendingWidgetAction()
            }
        }
    }

    private var metrics: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            metricTile("Температура", value: "\(store.snapshot.metrics.cpuTemperature, specifier: "%.1f") °C", symbol: "thermometer.medium")
            metricTile("Батарея", value: "\(store.snapshot.metrics.batteryHealthPercent)%", symbol: "battery.100percent")
            metricTile("Свободно", value: "\(store.snapshot.metrics.freeStorageGigabytes, specifier: "%.1f") ГБ", symbol: "externaldrive")
            metricTile("Вентилятор", value: "\(store.snapshot.metrics.fanRpm) об/мин", symbol: "fan")
        }
    }

    private func metricTile(_ title: String, value: String, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: symbol)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3.monospacedDigit().weight(.medium))
                .minimumScaleFactor(0.8)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}