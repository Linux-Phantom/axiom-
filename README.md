# Axiom

Axiom is a cross-platform system assistant and virtual robot companion. The Linux migration keeps the Kotlin Multiplatform agent core and adds a Linux desktop shell for hardware health, emotional status, and reviewed self-healing recommendations.

## Linux architecture

- **KMP core:** `commonMain` contains device models, agent dispatch, and provider fallback. `linuxX64` builds the native Linux library; the JVM desktop target can also run on Linux ARM64 where a compatible JDK is available.
- **Metrics:** `LinuxMetricsCollector` reads CPU temperatures and fan RPM from `/sys/class/hwmon`, battery full/design capacity from `/sys/class/power_supply`, CPU utilization from `/proc/stat`, memory from `/proc/meminfo`, and available disk space from the root filesystem. Missing hardware values are represented as `-1` or `null`; battery health is not guessed from charge level.
- **Desktop UI:** AWT `SystemTray` provides the desktop tray menu where supported; an always-on-top Swing window renders the Floating Mascot. Tray availability depends on the desktop environment and its XEmbed/AppIndicator support.
- **Agents:** The Medic uses Groq. The Companion and The Fixer use verified free OpenRouter model IDs with configured fallback models. Cloud diagnosis is started explicitly from the UI, not automatically on app launch.

## Build and run

Use JDK 21 or another Gradle-compatible JDK:

```sh
./gradlew jvmTest
./gradlew compileKotlinMetadata
./gradlew runLinuxMetricsProbe
./gradlew runLinuxDesktop
```

The desktop shell reads real metrics every 15 seconds. Select **Run diagnostics** from the tray or mascot window to send the current metric snapshot to the agents.

## API credentials

Prefer environment variables:

```sh
export GROQ_API_KEY='...'
export OPENROUTER_API_KEY_1='...'
export OPENROUTER_API_KEY_2='...'
```

Alternatively, create the ignored local `local.properties` file:

```properties
groq_api_key=...
openrouter_api_key.1=...
openrouter_api_key.2=...
```

Never put real credentials in tracked files such as `.env.example` or `local.properties.example`.

## Linux Self-Healing safety

The Fixer returns a diagnosis and a fixed set of command previews for installed tools: package-cache review (`apt`/`dnf`), `systemd-tmpfiles`, journald usage/retention, power-profile suggestions, and process inspection. Commands are never executed by Axiom. Every action is shown with its risk and privilege requirements and requires manual review. Process termination and destructive cleanup must be performed by the operator outside the application.

## Apple prototypes

`appleApp/` contains the earlier SwiftUI prototype. It is not part of the current Linux KMP targets or Linux desktop runtime.
