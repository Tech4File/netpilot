package app.netpilot.core.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.netpilot.R
import app.netpilot.core.model.DnsMode
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.PlatformVpnController
import app.netpilot.core.vpn.SecureDnsVpnService
import app.netpilot.core.vpn.VpnSessionState
import app.netpilot.core.vpn.VpnStatusMonitor
import app.netpilot.core.vpn.WireGuardManager
import app.netpilot.core.vpn.WireGuardRuntime

/**
 * The notification's single "Turn off" action: stops exactly what the
 * notification shows — the VPN tunnel, the Secure-DNS tunnel and/or strict
 * system Private DNS — in one tap, from anywhere.
 */
class StatusActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TURN_OFF) return
        val appContext = context.applicationContext
        val stopped = turnEverythingOff(appContext)
        VpnStatusService.stop(appContext)
        Toast.makeText(
            appContext,
            appContext.getString(if (stopped) R.string.notif_turned_off_toast else R.string.vpn_not_active_toast),
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val ACTION_TURN_OFF = "app.netpilot.action.TURN_OFF"

        /** Stops every NetPilot-controlled protection. @return true if anything was on. */
        fun turnEverythingOff(context: Context): Boolean {
            var stopped = false

            // 1) Platform-managed IKEv2 session.
            if (VpnSessionState.platformSessionActive(context)) {
                PlatformVpnController(context).stop()
                VpnSessionState.markPlatformStopped(context)
                stopped = true
            }

            // 2) OpenVPN transport service.
            if (NetPilotVpnService.isRunning) {
                // runCatching: a receiver can be delivered while the app is
                // background-restricted; the FGS itself makes this legal in
                // practice, but a background-start ban must never crash the
                // Turn-off action.
                runCatching {
                    context.startService(
                        Intent(context, NetPilotVpnService::class.java)
                            .setAction(NetPilotVpnService.ACTION_DISCONNECT),
                    )
                }
                stopped = true
            }

            // 2b) Embedded WireGuard tunnel (native turn-off, no network I/O).
            if (WireGuardRuntime.isRunning) {
                runCatching { WireGuardManager.get(context).disconnect() }
                stopped = true
            }

            // 3) Zero-setup Secure DNS tunnel.
            if (SecureDnsVpnService.runningHostname != null) {
                runCatching {
                    context.startService(
                        Intent(context, SecureDnsVpnService::class.java)
                            .setAction(SecureDnsVpnService.ACTION_STOP),
                    )
                }
                stopped = true
            }

            // 4) System strict Private DNS.
            if (PrivateDnsManager.read(context).mode == DnsMode.CUSTOM && PrivateDnsManager.disable(context)) {
                DnsProfileRepository(context).clearActive()
                stopped = true
            }

            // Fallback for tunnels the flags missed but the system still reports.
            if (!stopped && VpnStatusMonitor.isActive(context)) {
                PlatformVpnController(context).stop()
                stopped = true
            }
            return stopped
        }
    }
}
