package app.netpilot.openvpn.core

/**
 * The embedded OpenVPN engine — the only public surface of the
 * :openvpn-core module.
 *
 * This module deliberately depends on NOTHING from the app (the dependency
 * arrow is app -> openvpn-core). It speaks raw strings and file descriptors:
 *
 *   engine.start(rawOvpnText, tunFd, callbacks) -> Boolean
 *   engine.waitConnected(seconds) -> Boolean     // honest CONNECTED latch
 *   engine.stop()
 *
 * The TUN is opened and configured by NetPilotVpnService (app side); the fd
 * arrives already established. When the native library is absent every call
 * path is blocked at [OvpnCoreAvailability.isAvailable] — nothing here can
 * throw UnsatisfiedLinkError.
 */
class OvpnCoreEngine private constructor() {

    /** Callbacks into Kotlin; the instance is held for the session length. */
    interface Callbacks {
        /** Core event, e.g. CONNECTED / RECONNECTING / AUTH_FAILED. */
        fun onEvent(name: String, info: String, fatal: Boolean)

        /** One log line from the core. */
        fun onLog(line: String)

        /** VpnService.protect(fd) — keep core sockets outside the tunnel. */
        fun onProtect(fd: Int): Boolean
    }

    private val jni = Native()

    /** Starts the session over the already-established TUN fd. */
    fun start(configText: String, tunFd: Int, callbacks: Callbacks): Boolean =
        jni.nativeStart(callbacks, configText, tunFd)

    /** Blocks up to [seconds] waiting for the core's CONNECTED event. */
    fun waitConnected(seconds: Int): Boolean = jni.nativeWaitConnected(seconds)

    val isRunning: Boolean get() = jni.nativeIsRunning()

    fun stop() = jni.nativeStop()

    private class Native {
        external fun nativeStart(callbacks: Callbacks, config: String, tunFd: Int): Boolean
        external fun nativeWaitConnected(seconds: Int): Boolean
        external fun nativeIsRunning(): Boolean
        external fun nativeStop()

        companion object {
            init {
                System.loadLibrary("ovpncore")
            }
        }
    }

    companion object {
        /**
         * Returns an engine, or null when the native library is absent
         * (quality builds, CI quality job). The single entry gate.
         */
        @JvmStatic
        fun createOrNull(): OvpnCoreEngine? =
            if (OvpnCoreAvailability.isAvailable) OvpnCoreEngine() else null
    }
}
