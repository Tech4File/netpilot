package app.netpilot.core.boot

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import app.netpilot.core.prefs.AppPreferences
import app.netpilot.core.status.StatusNotifications
import app.netpilot.core.vpn.VpnProfileRepository
import app.netpilot.core.vpn.WgConfigCheck
import app.netpilot.core.vpn.WireGuardManager

/**
 * One-shot boot receiver (v2.2.0): fires exactly once per device power-on.
 * Decides via [BootReconnect]; either reconnects the last embedded WireGuard
 * tunnel or posts a single "tap to reconnect" notification. No scheduler,
 * no retry, no listeners afterwards — zero-dormancy holds.
 *
 * Manifest: RECEIVE_BOOT_COMPLETED; the broadcast is protected (system-only)
 * so the receiver is exported but not spoofable.
 */
class BootReconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val prefs = AppPreferences(appContext)
        val connectable = connectableProfile(appContext)
        val outcome = BootReconnect.decide(
            BootReconnect.State(
                reconnectOnBoot = prefs.reconnectOnBoot,
                lastWasEmbeddedWg = prefs.vpnWasActiveEmbeddedWg,
                hasConnectableWgProfile = connectable != null,
            ),
        )
        when (outcome) {
            BootReconnect.Outcome.CONNECT ->
                if (VpnService.prepare(appContext) == null) {
                    // Consent already granted (persists across reboots): start
                    // the tunnel headlessly. Failure → one honest notification.
                    WireGuardManager.get(appContext).connect(
                        connectable!!,
                        onResult = { ok, _ -> if (!ok) notify(appContext) },
                    )
                } else {
                    notify(appContext)
                }
            BootReconnect.Outcome.NOTIFY -> notify(appContext)
            BootReconnect.Outcome.SKIP -> Unit
        }
    }

    private fun connectableProfile(context: Context): VpnProfile? {
        val repo = VpnProfileRepository(context)
        fun VpnProfile.usable() = type == VpnType.WIREGUARD && !wgConfig.isNullOrBlank() &&
            WgConfigCheck.isPlausible(wgConfig!!)
        return repo.lastConnectedId()?.let { repo.get(it) }?.takeIf { it.usable() }
            ?: repo.list().firstOrNull { it.usable() }
    }

    private fun notify(context: Context) {
        StatusNotifications.ensureChannel(context)
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, StatusNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_shield)
            .setContentTitle(context.getString(R.string.boot_reconnect_title))
            .setContentText(context.getString(R.string.boot_reconnect_text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        // POST_NOTIFICATIONS is a runtime permission on 33+; the boot path
        // must never crash (or even vibrate) on a device that declined it.
        val canNotify = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIF_ID_BOOT, notification)
            }
        }
    }

    companion object {
        const val NOTIF_ID_BOOT = 47
    }
}
