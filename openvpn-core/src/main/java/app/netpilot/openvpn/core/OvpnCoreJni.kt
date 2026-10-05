package app.netpilot.openvpn.core

/**
 * JNI surface of libovpncore.so. One instance == one session.
 *
 * All methods are no-ops that throw [IllegalStateException] when the native
 * library is absent — callers MUST gate on [OvpnCoreAvailability.isAvailable]
 * (OvpnCoreInstaller does exactly that before wiring the factory).
 */
class OvpnCoreJni {

    /** Callbacks into Kotlin; the instance is held for the session length. */
    interface EngineCallbacks {
        /** Core event, e.g. CONNECTED / RECONNECTING / AUTH_FAILED. */
        fun onEvent(name: String, info: String, fatal: Boolean)

        /** One log line from the core. */
        fun onLog(line: String)

        /** VpnService.protect(fd) — MUST run on a thread-safe path. */
        fun onProtect(fd: Int): Boolean
    }

    private val jni = Native()

    /** Starts the session over the already-established TUN fd. */
    fun start(config: String, tunFd: Int, callbacks: EngineCallbacks): Boolean =
        jni.nativeStart(callbacks, config, tunFd)

    /** Blocks up to [seconds] waiting for the core's CONNECTED event. */
    fun waitConnected(seconds: Int): Boolean = jni.nativeWaitConnected(seconds)

    val isRunning: Boolean get() = jni.nativeIsRunning()

    fun stop() = jni.nativeStop()

    private class Native {
        external fun nativeStart(callbacks: EngineCallbacks, config: String, tunFd: Int): Boolean
        external fun nativeWaitConnected(seconds: Int): Boolean
        external fun nativeIsRunning(): Boolean
        external fun nativeStop()

        companion object {
            init {
                // Only reached via OvpnCoreAvailability.isAvailable == true;
                // the guard keeps the UnsatisfiedLinkError impossible here.
                System.loadLibrary("ovpncore")
            }
        }
    }
}
