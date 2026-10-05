package app.netpilot.ui.licenses

/**
 * In-app open-source attributions (Settings → Open-source licenses).
 *
 * NetPilot's RUNTIME code uses first-party Android libraries plus the two
 * embedded engines that make it fully self-sufficient: the Shizuku grant
 * bridge and the official WireGuard tunnel library. Everything else below is
 * build- or test-time tooling that is not part of the shipped APK, or the
 * platform itself — listed here in the spirit of full transparency that open
 * source deserves.
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
            "Shizuku API (RikkaApps)",
            "Optional integration: lets NetPilot receive the WRITE_SECURE_SETTINGS grant through the Shizuku server - no PC needed. Used only when the user opts in.",
            "MIT License",
            shipped = true,
        ),
        LicenseEntry(
            "WireGuard tunnel library (wireguard-android)",
            "The official WireGuard userspace engine embedded in NetPilot: runs WireGuard tunnels inside this app — no second app, no root. Includes wireguard-go (MIT). Copyright WireGuard LLC. WireGuard is a registered trademark of Jason A. Donenfeld.",
            "Apache License 2.0",
            shipped = true,
        ),
        LicenseEntry(
            "OpenVPN (embedded core + engine bridge)",
            "OpenVPN profiles connect through the embedded OpenVPN 3 core (source: github.com/openvpn/openvpn3, linked into the app at build time by CI) when the build ships the native library; otherwise they connect through the official open-source OpenVPN for Android app via its documented external control API. OpenVPN® is a registered trademark of OpenVPN, Inc.",
            "Embedded core: GNU AGPL-3.0 (source links in docs/OPENVPN_CORE.md) · bridge: external app",
            shipped = true,
        ),
        LicenseEntry(
            "desugar_jdk_libs (Google)",
            "Java library-desugaring runtime required by the WireGuard tunnel library.",
            "Apache License 2.0",
            shipped = true,
        ),
        LicenseEntry(
            "bundletool (Google)",
            "Builds the .apks split sets published with every release. Release-time tooling only.",
            "Apache License 2.0",
            shipped = false,
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
