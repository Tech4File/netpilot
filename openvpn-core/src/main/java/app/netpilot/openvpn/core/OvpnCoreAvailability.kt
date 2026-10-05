package app.netpilot.openvpn.core

/**
 * Runtime availability of the embedded OpenVPN engine.
 *
 * The native library is built by CI (scripts/build-ovpn-native.sh) and
 * packaged into release APKs. Builds without it (local quality builds, CI
 * quality job) keep working identically — the app falls back to the
 * sanctioned engine-bridge path. Availability is probed once.
 */
object OvpnCoreAvailability {

    @Volatile
    private var probed: Boolean = false

    @Volatile
    private var available: Boolean = false

    val isAvailable: Boolean
        get() {
            if (!probed) {
                synchronized(this) {
                    if (!probed) {
                        available = try {
                            System.loadLibrary("ovpncore")
                            true
                        } catch (_: UnsatisfiedLinkError) {
                            false
                        } catch (_: SecurityException) {
                            false
                        }
                        probed = true
                    }
                }
            }
            return available
        }
}
