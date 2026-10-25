import SwiftUI

@main
struct AxiomMacApp: App {
    @StateObject private var store = AxiomStateStore()

    var body: some Scene {
        MenuBarExtra("Axiom", systemImage: "sparkles") {
            MenuBarContentView()
                .environmentObject(store)
        }
        .menuBarExtraStyle(.window)

        Window("Axiom Companion", id: "axiom-floating-mascot") {
            FloatingMascotView()
                .environmentObject(store)
                .overlay {
                    FloatingWindowConfigurator()
                        .frame(width: 0, height: 0)
                }
        }
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentSize)
        .defaultSize(width: 300, height: 330)
    }
}