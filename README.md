# BMW E60 Coder Pro

Cross-platform mobile app for BMW E60/E61 vehicle tuning and diagnostics via OBD2/CAN bus.

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

Requires Android Studio with JDK 17.

```bash
./gradlew assembleRelease
```

APK is signed automatically via GitHub Actions.

## Safety Warning

This is an experimental research tool. Never use while vehicle is in motion without a co-pilot. All injection is disabled by default and requires explicit arming.

## License

MIT — Use at your own risk.
