# BMW E60 Coder Pro (Native Android)

Current full-source Android project for the BMW E60 Coder APK.

## Included now

- USB host serial communication for K+DCAN-style adapters
- Ethernet/TCP OBD communication
- Adapter presets for FTDI, CH340, ENET, and generic TCP bridges
- BMW-oriented KWP-style preset jobs
- E60 module-specific probe packs
- Decoded payload output with starter E60 dictionaries and live-data labels
- Target-aware comm profiles and adapter-level timing presets
- E60 vehicle profiles and ECU-family address-book switching
- Dedicated BMW service screens for DME, EGS, DSC, KOMBI, SZL, CAS, and FRM/LM
- Live home dashboard polling cards for DME, EGS, DSC, and CAS
- Daten parser / renderer with coding presets and patch preview
- CCC / tuning planning screens for Comfort, Sport, Race, and Custom map-slot workflows
- Steering retrofit helper screen for F-series wheel / paddle coding hints
- Flash-plan generator with dry-run and expert-plan modes
- Experimental preparation screens for remote-control and controller workflows

## What exceeds the original design document

The original design document was written for Expo/React Native. This project now exceeds that plan by using a native Android stack for lower-level transport support, adding dedicated service pages, live polling, E60 address books, comm profiles, payload decoders, coding presets, and flash-plan tooling.

## Build

1. Open in Android Studio.
2. Let Gradle sync.
3. Build `app` on a real Android device with OTG for USB testing.

## Notes

- Coding and flash planning are implemented as real local tooling.
- Flashing is dry-run by default.
- Experimental remote/gamepad features are intentionally kept in prepared or simulated mode by default rather than silently auto-arming live actuation.
- Treat the app as an advanced experimental BMW tool, not a full INPA / ISTA / EDIABAS replacement.


## Production hardening added

- Android USB permission request flow added for USB host serial devices
- Release build now enables code shrinking and resource shrinking
- JitPack repository added so `usb-serial-for-android` resolves during Gradle sync
- Lifecycle-aware Compose state collection
- App settings persistence for transport, preset, vehicle profile, polling interval, and selected screen


## Added in this build

- SZL/KOMBI/EGS steering retrofit bundle generator for F-series wheel to E-series retrofit planning
- Hardware validation matrix in `docs/HARDWARE_VALIDATION_MATRIX.md`
- SZL retrofit workflow in `docs/SZL_FSERIES_RETROFIT_WORKFLOW.md`
- JVM unit tests for DatenManager and SteeringRetrofitManager
