# Apple Axiom SwiftUI sources

These are source scaffolds; this repository does not currently contain an Xcode project.

## Target membership

- Add `Shared/AxiomModels.swift` and `Shared/AxiomMascotFace.swift` to the macOS app, iOS app, and Widget Extension targets.
- Add `Shared/AxiomStateStore.swift`, `Shared/AxiomMetricsFeed.swift`, and `Shared/AxiomCoreService.swift` to the macOS and iOS app targets.
- Add `macOS/*.swift` to a macOS 13+ app target.
- Add `iOS/AxiomIOSApp.swift` and `iOS/AxiomDashboardView.swift` to an iOS 17+ app target.
- Add `iOS/AxiomWidgets.swift` and `iOS/AxiomWidgetIntents.swift` to an iOS 17+ Widget Extension target.
- Add `iOS/AxiomLiveActivityController.swift` to the iOS app target.

Use separate app entry points/targets for `AxiomMacApp`, `AxiomIOSApp`, and `AxiomWidgetBundle`. Enable the App Groups capability on the iOS app and Widget Extension, and replace `group.com.apple.axiom` in `AxiomModels.swift` with the same App Group identifier registered for the developer account. Set `NSSupportsLiveActivities` to `YES` in the iOS app's Info.plist.

Add the `appleMain` Kotlin sources to the generated `AxiomShared` framework and link that framework from each app target. Store `groq_api_key`, `openrouter_api_key.1`, and optionally `openrouter_api_key.2` in Keychain using `AxiomCredentialStore`; never embed API keys in the app bundle. A platform sensor/monitor should publish each `AxiomDeviceMetrics` value to `AxiomMetricsFeed.shared.publish(_:)`. The state store immediately updates the mascot, menu bar, widgets, and Live Activity, then forwards the metrics to `AxiomCoreFacade` and applies The Companion/The Fixer results.

Widget self-healing buttons only queue an action ID in the App Group. The app asks for confirmation, then the KMP facade currently returns explicitly marked simulation results; real cache cleanup, process control, and power-management actions still need platform-specific implementations and their own authorization checks.