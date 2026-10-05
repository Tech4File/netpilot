package app.netpilot.core.boot

/**
 * "Reconnect VPN after device restart" (v2.2.0) — decision logic.
 *
 * Zero-dormancy compliant by construction: the boot receiver fires ONCE per
 * power-on, decides here, acts at most once, and exits. There is no
 * scheduler, no retry loop, no listening afterwards. If the reconnect fails
 * the user gets a single "tap to reconnect" notification — nothing more.
 */
object BootReconnect {

    enum class Outcome { CONNECT, NOTIFY, SKIP }

    data class State(
        /** User opted in via Settings (default OFF). */
        val reconnectOnBoot: Boolean,
        /** The tunnel that was up at shutdown was NetPilot's EMBEDDED WG. */
        val lastWasEmbeddedWg: Boolean,
        /** A connectable WireGuard profile still exists (id resolvable). */
        val hasConnectableWgProfile: Boolean,
    )

    /**
     * Platform IKEv2 sessions deliberately SKIP: the OS does not restore
     * them from a receiver, and Android's built-in "Always-on VPN" (Settings
     * → Network → VPN) already covers that case natively and better.
     */
    fun decide(s: State): Outcome = when {
        !s.reconnectOnBoot || !s.lastWasEmbeddedWg -> Outcome.SKIP
        s.hasConnectableWgProfile -> Outcome.CONNECT
        else -> Outcome.NOTIFY
    }
}
