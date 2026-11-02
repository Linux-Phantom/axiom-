import SwiftUI

struct MenuBarContentView: View {
    @EnvironmentObject private var store: AxiomStateStore
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                AxiomMascotFace(mood: store.snapshot.mood, size: 58)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Axiom")
                        .font(.headline)
                    Label(store.snapshot.mood.title, systemImage: store.snapshot.mood.symbolName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 4)
            }

            Divider()

            VStack(alignment: .leading, spacing: 7) {
                metric("CPU", value: "\(store.snapshot.metrics.cpuTemperature, specifier: "%.1f") °C", symbol: "thermometer.medium")
                metric("Аккумулятор", value: "\(store.snapshot.metrics.batteryHealthPercent)%", symbol: "battery.100percent")
                metric("Свободно", value: "\(store.snapshot.metrics.freeStorageGigabytes, specifier: "%.1f") ГБ", symbol: "externaldrive")
                metric("Вентилятор", value: "\(store.snapshot.metrics.fanRpm) об/мин", symbol: "fan")
            }

            if let reply = store.snapshot.companionReply {
                Text(reply.text)
                    .font(.callout)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
            }

            Button {
                store.analyzeCurrentMetrics()
            } label: {
                Label("Запустить диагностику", systemImage: "stethoscope")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.borderedProminent)
            .disabled(store.isAnalyzing)

            AxiomFixActionList()

            Button {
                openWindow(id: "axiom-floating-mascot")
            } label: {
                Label("Показать компаньона", systemImage: "macwindow.on.rectangle")
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(width: 330)
        .onAppear {
            store.startMonitoring()
            store.consumePendingWidgetAction()
        }
    }

    private func metric(_ title: String, value: String, symbol: String) -> some View {
        HStack {
            Label(title, systemImage: symbol)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .monospacedDigit()
        }
        .font(.caption)
    }
}