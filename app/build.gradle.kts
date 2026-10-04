import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningVariables = listOf(
    "MWALAB_RELEASE_STORE_FILE",
    "MWALAB_RELEASE_STORE_PASSWORD",
    "MWALAB_RELEASE_KEY_ALIAS",
    "MWALAB_RELEASE_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val releaseSigningRequested = releaseSigningVariables.values.any { it != null }
if (releaseSigningRequested) {
    val missing = releaseSigningVariables.filterValues { it.isNullOrBlank() }.keys
    if (missing.isNotEmpty()) {
        throw GradleException("Incomplete release signing environment: missing ${missing.joinToString()}")
    }
    val storeFile = File(releaseSigningVariables.getValue("MWALAB_RELEASE_STORE_FILE")!!)
    if (!storeFile.isAbsolute || !storeFile.isFile ||
        storeFile.canonicalFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())
    ) {
        throw GradleException("Release keystore must be an existing absolute file outside the repository")
    }
}

android {
    namespace = "dev.mwalab"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.mwalab"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-clockin"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Live Devnet acceptance is run only by an explicit adb instrumentation command.
        testInstrumentationRunnerArguments["notClass"] = "dev.mwalab.wallet.Phase9LiveSendInstrumentedTest"
    }

    signingConfigs {
        if (releaseSigningRequested) {
            create("operatorRelease") {
                storeFile = file(releaseSigningVariables.getValue("MWALAB_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningVariables.getValue("MWALAB_RELEASE_STORE_PASSWORD")!!
                keyAlias = releaseSigningVariables.getValue("MWALAB_RELEASE_KEY_ALIAS")!!
                keyPassword = releaseSigningVariables.getValue("MWALAB_RELEASE_KEY_PASSWORD")!!
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("operatorRelease")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("androidTest").assets.directories.add(rootProject.file("test-vectors/simulation").path)
    }
    sourceSets.getByName("test").resources.directories.add(rootProject.file("test-vectors/transactions").path)

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

// Phase 6 fault vectors live at the repository root as an independent,
// machine-readable contract. Pass the canonical file path explicitly to local
// JVM tests instead of relying on AGP to package an external directory as a
// classpath resource. This keeps one authoritative vector file and works with
// lazily-created Android unit-test tasks.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty(
        "mwalab.phase6.faultVectorsPath",
        rootProject.file("test-vectors/faults/faults.properties").absolutePath,
    )
}

dependencies {
    implementation(libs.solana.mobile.walletlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.multimult)
    implementation(libs.bouncycastle)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.solana.mobile.clientlib)
    androidTestImplementation(libs.androidx.room.testing)
    // Room migration-test serializers need the 1.8 JVM default-method ABI.
    // AGP aligns instrumentation dependencies to the target debug runtime.
    debugImplementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
