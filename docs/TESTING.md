# Testing

Baseline build gates:

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

Future phase gates will cover:

- protocol behavior;
- trace integrity;
- capability snapshots;
- deterministic faults;
- persistence;
- security;
- transaction diagnostics;
- emulator/physical-device behavior;
- release APK testing.

A successful happy-path demo alone does not constitute a passing build.
