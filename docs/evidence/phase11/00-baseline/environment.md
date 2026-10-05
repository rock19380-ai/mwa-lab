# Toolchain and dependency baseline

Observed 2026-10-05 on local Linux x86_64, kernel `6.17.0-20-generic`:

- Gradle wrapper `9.6.0`, distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`; `./gradlew --version` succeeded.
- Launcher OpenJDK `21.0.12.1`; Gradle daemon JVM provisioned as Java `25` by `gradle/gradle-daemon-jvm.properties`. CI uses Temurin Java 25.
- Android SDK: `ANDROID_HOME=/home/abbaas/Android/Sdk`; installed build tools `36.0.0`. No local path is a release requirement.
- Version catalog: Android Gradle Plugin `9.4.1`, Kotlin `2.2.10`, Room `2.8.5`, walletlib/clientlib `2.0.7`. No dependency changed in Phase 11.
- `settings.gradle.kts` uses Google, Maven Central, and Gradle Plugin Portal; root modules are `:app` and `:demo-client`.
- Release signing requires four operator-provided environment variables and a keystore outside the repository; none are recorded here. CI assembles unsigned release APKs only.

The frozen `gradle/libs.versions.toml`, `settings.gradle.kts`, `gradle.properties` and app build configuration are included in the protected manifest. Local Gradle cache availability does not prove clean-network dependency resolution; a separate checkout test checks source self-sufficiency.
