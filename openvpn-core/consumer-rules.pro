# JNI surface of libovpncore.so — names are resolved at runtime by the
# native library (JNI_OnLoad + JNIEnv::GetMethodID / native* lookup), so
# neither the native method declarations nor the callback interface may be
# renamed or removed by R8.
# OvpnNative is the top-level JNI binding object (the native* symbols are
# derived from ITS name); OvpnCoreEngine is the facade + Callbacks host.
-keep class app.netpilot.openvpn.core.OvpnNative { *; }
-keep class app.netpilot.openvpn.core.OvpnCoreEngine$Callbacks { *; }
-keep class app.netpilot.openvpn.core.OvpnCoreEngine { *; }
