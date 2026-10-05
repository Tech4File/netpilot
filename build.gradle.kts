// NetPilot — root build configuration.
// Runtime dependencies are first-party Android (Jetpack/AndroidX/Material) only.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
