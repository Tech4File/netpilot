package app.netpilot.ui.licenses

/**
 * In-app open-source attributions (Settings → Open-source licenses).
 *
 * NetPilot's RUNTIME code uses only first-party Android libraries
 * (AndroidX/Jetpack, Material Components, Kotlin stdlib, platform APIs).
 * Everything else below is build- or test-time tooling that is not part of the
 * shipped APK, or the platform itself — listed here in the spirit of full
 * transparency that open source deserves.
 */
data class LicenseEntry(
    val name: String,
    val description: String,
    val license: String,
    val shipped: Boolean,
)

object LicenseCatalog {

    val entries: List<LicenseEntry> = listOf(
        LicenseEntry(
            "AndroidX / Jetpack",
            "Google's official Android extension libraries: core-ktx, appcompat, fragments, recyclerview, constraintlayout, lifecycle, annotation.",
            "Apache License 2.0",
            shipped = true,
        ),
        LicenseEntry(
            "Material Components for Android",
            "Google's Material 3 component library powering the UI system.",
            "Apache License 2.0",
            shipped = true,
        ),
        LicenseEntry(
            "Kotlin & Kotlin Standard Library",
            "Language and standard library by JetBrains, first-party for Android development.",
            "Apache License 2.0",
            shipped = true,
        ),
        LicenseEntry(
            "Android SDK / OpenJDK",
            "The Android platform toolchain and OpenJDK-derived APIs.",
            "Apache License 2.0 / GPL+CE",
            shipped = true,
        ),
        LicenseEntry(
            "Gradle",
            "Build automation for compiling, testing and packaging. Build-time only.",
            "Apache License 2.0",
            shipped = false,
        ),
        LicenseEntry(
            "JUnit 4",
            "Unit test framework used by the automated test suites. Test-time only.",
            "Eclipse Public License 1.0",
            shipped = false,
        ),
        LicenseEntry(
            "Robolectric",
            "JVM simulation of Android for fast, pre-APK test automation. Test-time only.",
            "MIT License",
            shipped = false,
        ),
        LicenseEntry(
            "Espresso (AndroidX Test)",
            "UI automation framework used on emulators in CI. Test-time only.",
            "Apache License 2.0",
            shipped = false,
        ),
    )
}
