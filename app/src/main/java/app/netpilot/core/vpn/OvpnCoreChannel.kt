package app.netpilot.core.vpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import app.netpilot.openvpn.core.OvpnCoreEngine
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-side adapter: NetPilot's [VpnDataChannel] seam over the embedded
 * OpenVPN engine (openvpn-core module, AGPL-3.0, CI-built native library).
 *
 * Contract (NetPilotVpnService): the TUN is already established when open()
 * runs — the fd and the raw .ovpn text go to the core; "up" means the
 * core's own CONNECTED event within a bounded window. Without the native
 * library the engine is null and the factory is never wired, so this class
 * never runs in a degraded state.
 */
class OvpnCoreChannel(context: Context) : VpnDataChannel {

    // The factory is invoked with the RUNNING VpnService — keep that exact
    // instance for protect(). Using applicationContext here would make the
    // VpnService cast fail, and EVERY core socket would go unprotected,
    // deadlocking the tunnel inside itself.
    private val vpnContext: Context = context
    private val stopping = AtomicBoolean(false)

    @Volatile
    private var engine: OvpnCoreEngine? = null

    /**
     * The TUN fd this channel took over with detachFd(). The channel OWNS it:
     * closing it is what actually releases the system VPN (the detached
     * ParcelFileDescriptor the service holds is a no-op on close). Kept as an
     * int so the release path can adopt-and-close it even after a failed
     * start.
     */
    @Volatile
    private var ownedTunFd: Int = -1

    override fun open(tun: ParcelFileDescriptor, config: OvpnConfig): Boolean {
        val raw = config.raw
        if (raw.isNullOrBlank()) {
            // The parser always sets raw; a null means a hand-built config —
            // refuse honestly instead of guessing.
            Log.w(TAG, "open: config carries no raw text")
            return false
        }
        val engine = OvpnCoreEngine.createOrNull()
        if (engine == null) {
            Log.w(TAG, "open: native engine absent on this build")
            return false
        }
        this.engine = engine
        stopping.set(false)

        val fd = tun.detachFd()
        ownedTunFd = fd
        val started = engine.start(raw, fd, Callbacks(vpnContext))
        if (!started) {
            releaseTunFd()
            Log.w(TAG, "open: core rejected the profile")
            return false
        }
        val up = engine.waitConnected(CONNECT_TIMEOUT_S)
        if (!up) {
            Log.w(TAG, "open: no CONNECTED within ${CONNECT_TIMEOUT_S}s")
            stopSession()
            return false
        }
        return true
    }

    override fun close() = stopSession()

    private fun stopSession() {
        if (stopping.getAndSet(true)) return
        // ORDER IS THE FIX: close the TUN fd FIRST. That is the moment the
        // system VPN disappears and normal internet routing is restored, and
        // the dead fd breaks the core out of its packet loop so the stop and
        // its join finish promptly. Stopping the engine first risked the core
        // still holding an open TUN through a slow stop handshake — the
        // field-reported "VPN stays up until force-stop".
        releaseTunFd()
        runCatching { engine?.stop() }
        engine = null
    }

    private fun releaseTunFd() {
        val fd = ownedTunFd
        ownedTunFd = -1
        if (fd >= 0) closeFd(fd)
    }

    private fun closeFd(fd: Int) {
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
    }

    /** JNI callbacks: protect (routing-loop safety) + honest event logging. */
    private class Callbacks(private val vpn: Context) : OvpnCoreEngine.Callbacks {

        override fun onEvent(name: String, info: String, fatal: Boolean) {
            Log.i(TAG, buildString {
                append("event: ").append(name)
                if (info.isNotBlank()) append(" (").append(info).append(')')
                if (fatal) append(" FATAL")
            })
        }

        override fun onLog(line: String) {
            Log.d(TAG, if (line.length > LOG_MAX) line.take(LOG_MAX) else line)
        }

        override fun onProtect(fd: Int): Boolean = try {
            val svc = vpn as? VpnService
            if (svc == null) {
                Log.w(TAG, "protect: context is not the VpnService — socket left unprotected")
                false
            } else {
                svc.protect(fd)
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NetPilotOvpn"
        private const val LOG_MAX = 400

        /** Bounded, honest wait for CONNECTED (server timeouts land here). */
        const val CONNECT_TIMEOUT_S = 30
    }
}

/**
 * Wires the embedded engine into [VpnDataChannel.factory] once, when the
 * native library is present. Absent library -> factory untouched -> the app
 * keeps its existing honest engine-bridge guidance.
 */
object OvpnCoreInstall {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            if (app.netpilot.openvpn.core.OvpnCoreAvailability.isAvailable) {
                VpnDataChannel.factory = { context -> OvpnCoreChannel(context) }
            }
            installed = true
        }
    }

    /**
     * Installs (if needed) and reports whether the embedded engine is wired.
     * Call sites that must DECIDE now use this instead of reading the factory
     * directly, so the decision can never race or depend on install order.
     */
    fun ensureInstalled(): Boolean {
        install()
        return VpnDataChannel.factory != null
    }
}
