import SwiftUI

struct FloatingMascotView: View {
    @EnvironmentObject private var store: AxiomStateStore

    var body: some View {
        VStack(spacing: 14) {
            AxiomMascotFace(mood: store.snapshot.mood, size: 132)

            VStack(spacing: 4) {
                Text(store.snapshot.mood.title)
                    .font(.headline)
                Text(store.snapshot.companionReply?.text ?? "Я слежу за состоянием устройства.")
                    .font(.callout)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 14) {
                Label("\(store.snapshot.metrics.cpuTemperature, specifier: "%.0f")°", systemImage: "thermometer.medium")
                Label("\(store.snapshot.metrics.batteryHealthPercent)%", systemImage: "battery.100percent")
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)

            AxiomFixActionList()
        }
        .padding(20)
        .frame(width: 300)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}