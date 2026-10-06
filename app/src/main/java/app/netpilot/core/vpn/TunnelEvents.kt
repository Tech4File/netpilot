package app.netpilot.core.vpn

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide "the tunnel situation changed" event bus.
 *
 * Every real transition (service started/torn down, WireGuard tunnel up/down,
 * Secure-DNS tunnel up/down) notifies this hub; visible screens subscribe and
 * re-render from the authoritative sources ([VpnSessionState], service flags,
 * [WireGuardRuntime]). This is what keeps the dashboard hero, the VPN switch,
 * the profile rows and the status notification converging on the truth within
 * milliseconds of a transition — instead of drifting until the next
 * onResume.
 *
 * Deliberately dumb: no state of its own, no threading guarantees. Notifiers
 * dispatch on the main thread ([notifyChangedAsync] when the caller may be on
 * a binder/worker thread); listeners must be cheap and re-read state.
 */
object TunnelEvents {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var mainHandler: Handler? = null

    /** Register a change listener (fragment onStart / onResume). */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /** Unregister (onPause / onStop / onDestroyView). */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Fire synchronously on the CALLER'S thread — call from the main thread. */
    fun notifyChanged() {
        for (l in listeners) {
            runCatching { l() }
        }
    }

    /** Fire on the main loop — safe from any thread. */
    fun notifyChangedAsync() {
        val handler = mainHandler ?: Handler(Looper.getMainLooper()).also { mainHandler = it }
        handler.post { notifyChanged() }
    }

    /** Test hook. */
    internal fun clearForTest() {
        listeners.clear()
        mainHandler = null
    }
}
