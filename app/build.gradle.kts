import java.util.Properties

/*
 * NetPilot — application module.
 *
 * Dependency policy (see docs/SECURITY.md):
 *  - Runtime: first-party Android libraries only (AndroidX / Material / Kotlin stdlib).
 *  - Test & build tooling: standard open-source DevOps tools (JUnit, Robolectric, Espresso).
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val releaseSigningActive: Boolean = listOf(
    "ANDROID_KEYSTORE_B64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
).all { System.getenv(it) != null }

android {
    namespace = "app.netpilot"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.netpilot"
        // Android 9 (Pie) — the release that introduced system-wide Private DNS.
        minSdk = 28
        targetSdk = 35
        // RELEASE RULE: a push to main auto-publishes a GitHub Release whenever
        // versionName below is NEW (no matching vX.Y.Z tag exists yet). No bump
        // => the Release run checks and skips gracefully. versionCode must +1
        // with every release so signed APKs install over the previous ones.
        versionCode = 21
        versionName = "2.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        resourceConfigurations += listOf("en")
    }

    if (releaseSigningActive) {
        signingConfigs {
            create("release") {
                storeFile = File("$rootDir/.ci-release-keystore.jks") // decoded by release.yml from secrets
                storeType = "PKCS12"
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    // Per-ABI APKs for the release channel (plus the universal one).
    // NetPilot carries exactly ONE native component: the embedded WireGuard
    // userspace engine (official tunnel library) — so the per-ABI splits now
    // also save real download size; the UNIVERSAL APK remains the
    // recommended download.
    splits {
        abi {
            // Enabled only for APK builds: ./gradlew :app:assembleRelease -PabiSplits
            // (bundleRelease runs without the flag - AAB + splits are mutually
            // exclusive in AGP, and the bundle already splits per-device itself.)
            isEnable = project.hasProperty("abiSplits")
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningActive) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Unsigned release builds are produced when CI secrets are absent;
                // the release workflow blocks publishing in that case (fail-safe).
                signingConfig = null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by the embedded WireGuard tunnel library (official).
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true // Shizuku user-service interface (no-PC permission grant)
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "640m"
                test.maxParallelForks = 1
            }
        }
    }

    lint {
        // Local low-RAM sandboxes: -PskipReleaseLint skips lintVital inside
        // assembleRelease (CI always runs it; lintDebug gates everything).
        checkReleaseBuilds = !project.hasProperty("skipReleaseLint")
        abortOnError = true
        warningsAsErrors = false
        disable += listOf("GradleDependency", "GoogleAppIndexingWarning")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
}

dependencies {
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // ---- Embedded WireGuard engine (official tunnel library, Apache-2.0) ---
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    // ---- Runtime: first-party Android only --------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.components)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.annotation)

    // ---- Unit tests (JVM + Robolectric device simulation) -----------------
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)

    // ---- Instrumented tests (emulator / device) ---------------------------
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.intents)
}
