package app.netpilot.core.boot

import app.netpilot.core.boot.BootReconnect.Outcome
import app.netpilot.core.boot.BootReconnect.State
import org.junit.Assert.assertEquals
import org.junit.Test

class BootReconnectTest {

    @Test
    fun `opted out skips — zero-dormancy default`() {
        assertEquals(
            Outcome.SKIP,
            BootReconnect.decide(State(reconnectOnBoot = false, lastWasEmbeddedWg = true, hasConnectableWgProfile = true)),
        )
    }

    @Test
    fun `last tunnel was not embedded wireguard skips`() {
        // Platform IKEv2: the OS's Always-on VPN owns this case.
        assertEquals(
            Outcome.SKIP,
            BootReconnect.decide(State(reconnectOnBoot = true, lastWasEmbeddedWg = false, hasConnectableWgProfile = true)),
        )
    }

    @Test
    fun `connectable profile reconnects once`() {
        assertEquals(
            Outcome.CONNECT,
            BootReconnect.decide(State(reconnectOnBoot = true, lastWasEmbeddedWg = true, hasConnectableWgProfile = true)),
        )
    }

    @Test
    fun `deleted profile notifies instead of failing silently`() {
        assertEquals(
            Outcome.NOTIFY,
            BootReconnect.decide(State(reconnectOnBoot = true, lastWasEmbeddedWg = true, hasConnectableWgProfile = false)),
        )
    }
}
