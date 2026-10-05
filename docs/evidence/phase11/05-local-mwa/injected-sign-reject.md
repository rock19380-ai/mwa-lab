# Injected signing rejection — pending signed RC2

October 5: **NOT RUN on RC2**. The existing debug-only fault-persistence instrumentation passed on `emulator-5556`: its *test-specific* private preferences start NORMAL, select `FAULT_SIGN_REJECT`, restore NORMAL, and fail closed to NORMAL on corruption. That does **not** prove an RC2 protocol `ERROR_NOT_SIGNED (-3)`, INJECTED attribution, absence of submission, persisted report, or RC2's final active fault. **Final RC2 fault state: UNKNOWN; RC2 not installed.** Restore NORMAL and force-stop/relaunch as part of real RC2 acceptance.
