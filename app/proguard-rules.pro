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
