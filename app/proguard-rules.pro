# NetPilot R8 rules.
# The app has no reflection-heavy third-party dependencies; the default
# proguard-android-optimize rules plus the entries below are sufficient.

# Keep crash-report legibility for our own code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep the pluggable VPN transport surface (external engine modules implement
# these classes; they are loaded by name).
-keep interface app.netpilot.core.vpn.VpnDataChannel { *; }
-keep class app.netpilot.core.vpn.OpenVpnTunConfig { *; }

# org.json is part of the Android platform — never obfuscate our JSON DTO field names
# used by profile repositories.
-keepclassmembers class app.netpilot.core.model.** { <fields>; }

# Embedded WireGuard engine (official wireguard-android tunnel library).
# The AAR ships NO consumer rules of its own, and the engine is JNI-bound:
# native methods (wgTurnOn/wgTurnOff/...) must keep their exact names, and
# the crypto/config/backend classes are reflected by the library. Never
# shrink or obfuscate any of it — size cost is minor, breakage is total.
-keep class com.wireguard.** { *; }
-keepclassmembers class com.wireguard.** { *; }
-dontwarn com.wireguard.**
# Keep record components (Statistics$PeerStats is a Java record).
-keepattributes RecordAttribute
