import SwiftUI

@main
struct AxiomIOSApp: App {
    @StateObject private var store = AxiomStateStore()

    var body: some Scene {
        WindowGroup {
            AxiomDashboardView()
                .environmentObject(store)
        }
    }
}