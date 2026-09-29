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
        versionCode = 5
        versionName = "1.1.3"
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
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
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
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += listOf("GradleDependency", "GoogleAppIndexingWarning")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
}

dependencies {
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
