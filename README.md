# BMW E60 Coder Pro

Cross-platform mobile app for BMW E60/E61 vehicle tuning and diagnostics via OBD2/CAN bus.

**Android / BMW hacking genius edition** — production APK ready via GitHub Actions.

## Features

- **KWP2000/BMW-FAST Protocol** — Full diagnostic session management
- **Live Data Polling** — Real-time DME, EGS, DSC, KOMBI, SZL, CAS, FRM, ACSM, CCC data
- **DTC Reading/Clearing** — 100+ module-specific fault codes
- **Coding Engine** — Read and write module coding via KWP 0x3B
- **CAS Remote Start/Stop** — Engine control sequences (experimental)
- **Xbox Controller Bridge** — USB gamepad to vehicle input mapping
- **Multi-Adapter Support** — USB FTDI, ELM327 Bluetooth/WiFi, ENET WiFi

## Supported Adapters

| Adapter | Connection | Protocol |
|---------|-----------|----------|
| FTDI FT232RL | USB | K+DCAN |
| ELM327 (PIC18F25K80) | Bluetooth/WiFi | KWP2000/BMW-FAST |
| ENET Cable | WiFi | BMW-FAST |
| CH340/CP2102 | USB | K+DCAN |

## CAN Bus Support

- **PT-CAN (500 kbps)** — Engine, transmission, DSC, steering
- **K-CAN (100 kbps)** — Doors, windows, climate, iDrive, lighting

## Building

Requires Android Studio / JDK 17 + Android SDK.

```bash
# Preferred (wrapper)
./gradlew assembleDebug
./gradlew assembleRelease   # needs keystore secrets or local release.keystore

# Or with system Gradle 8.7+
gradle assembleDebug
```

APK artifacts are produced automatically by GitHub Actions on every push to `main`.

- Debug APK: always uploaded
- Release APK: uploaded when signing secrets are configured

### GitHub Secrets for signed release

| Secret | Description |
|--------|-------------|
| `KEYSTORE_PATH` | Path to keystore inside runner (or upload as artifact) |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

## Safety Warning

This is an experimental research tool. Never use while vehicle is in motion without a co-pilot. All injection is disabled by default and requires explicit arming.

**Use at your own risk.** Incorrect coding can brick modules. Always backup original coding data first.

## License

MIT — Use at your own risk.
