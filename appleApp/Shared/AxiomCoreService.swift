import Foundation
import Security

#if canImport(AxiomShared)
import AxiomShared
#endif

enum AxiomCredential: String {
    case groq = "groq_api_key"
    case openRouterPrimary = "openrouter_api_key.1"
    case openRouterFallback = "openrouter_api_key.2"
}

enum AxiomCredentialStore {
    private static let service = "com.apple.axiom.credentials"

    static func read(_ credential: AxiomCredential) throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: credential.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw AxiomCredentialError.keychain(status)
        }
        return String(data: data, encoding: .utf8)
    }

    static func save(_ value: String, for credential: AxiomCredential) throws {
        let key: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: credential.rawValue,
        ]
        let update: [String: Any] = [
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(key as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = key
            update.forEach { insert[$0.key] = $0.value }
            let insertStatus = SecItemAdd(insert as CFDictionary, nil)
            guard insertStatus == errSecSuccess else { throw AxiomCredentialError.keychain(insertStatus) }
        } else if status != errSecSuccess {
            throw AxiomCredentialError.keychain(status)
        }
    }
}

enum AxiomCredentialError: LocalizedError {
    case keychain(OSStatus)
    case missing(String)
    case frameworkUnavailable

    var errorDescription: String? {
        switch self {
        case .keychain:
            "Не удалось получить credentials из Keychain."
        case .missing(let name):
            "Добавьте \(name) в Keychain, чтобы подключить Axiom Core."
        case .frameworkUnavailable:
            "Подключите AxiomShared.framework к app target в Xcode."
        }
    }
}

struct AxiomCoreAnalysis {
    let metrics: AxiomDeviceMetrics
    let mood: AxiomMood
    let companionMessage: String
    let fixerMessage: String
    let actions: [AxiomFixAction]
    let updatedAt: Date
}

struct AxiomCoreActionOutcome {
    let message: String
    let simulated: Bool
}

protocol AxiomCoreServicing: AnyObject {
    func analyze(
        _ metrics: AxiomDeviceMetrics,
        completion: @escaping (Result<AxiomCoreAnalysis, Error>) -> Void
    )
    func performAction(
        actionID: String,
        completion: @escaping (Result<AxiomCoreActionOutcome, Error>) -> Void
    )
}

enum AxiomCoreServiceFactory {
    static var unavailableMessage: String {
        AxiomCredentialStore.readinessMessage
    }

    static func make() -> AxiomCoreServicing? {
        #if canImport(AxiomShared)
        let groq: String
        let openRouter: String
        let fallback: String?
        do {
            guard let storedGroq = try AxiomCredentialStore.read(.groq),
                  let storedOpenRouter = try AxiomCredentialStore.read(.openRouterPrimary) else {
                return nil
            }
            groq = storedGroq
            openRouter = storedOpenRouter
            fallback = try AxiomCredentialStore.read(.openRouterFallback)
        } catch {
            return nil
        }
        return KmpAxiomCoreService(
            facade: AxiomShared.AxiomCoreFacade(
                groqApiKey: groq,
                openRouterApiKey: openRouter,
                openRouterFallbackApiKey: fallback ?? nil
            )
        )
        #else
        return nil
        #endif
    }
}

private extension AxiomCredentialStore {
    static var readinessMessage: String {
        #if canImport(AxiomShared)
        "Добавьте Groq и OpenRouter credentials в Keychain."
        #else
        AxiomCredentialError.frameworkUnavailable.localizedDescription
        #endif
    }
}

#if canImport(AxiomShared)
private final class KmpAxiomCoreService: AxiomCoreServicing {
    private let facade: AxiomShared.AxiomCoreFacade

    init(facade: AxiomShared.AxiomCoreFacade) {
        self.facade = facade
    }

    deinit {
        facade.close()
    }

    func analyze(
        _ metrics: AxiomDeviceMetrics,
        completion: @escaping (Result<AxiomCoreAnalysis, Error>) -> Void
    ) {
        let coreMetrics = AxiomShared.DeviceMetrics(
            cpuTemperature: metrics.cpuTemperature,
            batteryHealthPercent: Int32(metrics.batteryHealthPercent),
            freeStorageGigabytes: metrics.freeStorageGigabytes,
            fanRpm: Int32(metrics.fanRpm)
        )
        facade.analyze(metrics: coreMetrics) { update, errorMessage in
            guard let update else {
                completion(.failure(AxiomBridgeError(message: errorMessage ?? "Axiom Core не вернул результат.")))
                return
            }
            let actions = update.actions.map { action in
                AxiomFixAction(
                    id: action.id,
                    title: action.title,
                    detail: action.detail,
                    systemImage: Self.symbol(for: action.id),
                    confirmationMessage: action.confirmationMessage
                )
            }
            completion(.success(
                AxiomCoreAnalysis(
                    metrics: metrics,
                    mood: AxiomMood(rawValue: update.mood) ?? .happy,
                    companionMessage: update.companionMessage,
                    fixerMessage: update.fixerMessage,
                    actions: actions,
                    updatedAt: Date(timeIntervalSince1970: Double(update.updatedAtEpochMillis) / 1_000)
                )
            ))
        }
    }

    func performAction(
        actionID: String,
        completion: @escaping (Result<AxiomCoreActionOutcome, Error>) -> Void
    ) {
        facade.performAction(actionId: actionID) { result, errorMessage in
            guard let result else {
                completion(.failure(AxiomBridgeError(message: errorMessage ?? "Действие не выполнено.")))
                return
            }
            completion(.success(AxiomCoreActionOutcome(message: result.message, simulated: result.simulated)))
        }
    }

    func close() {
        facade.close()
    }

    private static func symbol(for actionID: String) -> String {
        switch actionID {
        case "reduce-load": "gauge.with.dots.needle.50percent"
        case "review-storage": "externaldrive"
        case "battery-care": "battery.50percent"
        default: "checklist"
        }
    }
}

private struct AxiomBridgeError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
#endif