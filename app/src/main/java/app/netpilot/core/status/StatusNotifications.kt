package app.netpilot.core.status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.netpilot.MainActivity
import app.netpilot.R
import app.netpilot.core.model.DnsMode
import app.netpilot.core.dns.DnsProfileRepository
import app.netpilot.core.dns.PrivateDnsManager
import app.netpilot.core.vpn.NetPilotVpnService
import app.netpilot.core.vpn.SecureDnsVpnService
import app.netpilot.core.vpn.VpnSessionState

/**
 * The ONE status notification for everything NetPilot keeps active.
 *
 * - VPN tunnel (OpenVPN transport or platform IKEv2)  → shown with the profile name
 * - Zero-setup Secure DNS tunnel                      → shown with the provider host
 * - System strict Private DNS                         → shown with the profile name
 *
 * Two or all three at once → a single notification listing every active line
 * with ONE "Turn off" action that stops everything it shows. The notification
 * is hosted by whichever component is alive: NetPilotVpnService (id 41),
 * SecureDnsVpnService (id 43) or VpnStatusService (id 45) — the standalone
 * host for states that have no in-process service (platform IKEv2, DNS-only).
 */
object StatusNotifications {

    const val CHANNEL_ID = "netpilot_status"
    const val NOTIF_ID_VPN = 41
    const val NOTIF_ID_SECURE_DNS = 43
    const val NOTIF_ID_STATUS = 45

    /** What NetPilot currently keeps active. */
    data class Snapshot(
        val vpnName: String?,
        val secureDnsHost: String?,
        val privateDnsName: String?,
    ) {
        val isEmpty: Boolean get() = vpnName == null && secureDnsHost == null && privateDnsName == null
    }

    fun snapshot(context: Context): Snapshot {
        val appContext = context.applicationContext
        val vpnName = when {
            NetPilotVpnService.isRunning ->
                NetPilotVpnService.runningSession ?: appContext.getString(R.string.app_name)
            VpnSessionState.platformSessionActive(appContext) ->
                VpnSessionState.platformProfileName(appContext) ?: appContext.getString(R.string.app_name)
            else -> null
        }
        val secureHost = SecureDnsVpnService.runningHostname?.takeIf { it.isNotBlank() }
        val dnsState = PrivateDnsManager.read(appContext)
        val privateDnsName = if (dnsState.mode == DnsMode.CUSTOM) {
            DnsProfileRepository(appContext).activeProfile()?.name ?: dnsState.specifier
        } else {
            null
        }
        return Snapshot(vpnName, secureHost, privateDnsName)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.setShowBadge(false)
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    /** The unified notification, or null when nothing is active. */
    fun build(context: Context, snap: Snapshot): Notification? {
        if (snap.isEmpty) return null
        val both: Pair<String, String>? = when {
            snap.vpnName != null && snap.privateDnsName != null ->
                context.getString(R.string.notif_both_title) to
                    context.getString(R.string.notif_both_text, snap.vpnName, snap.privateDnsName)
            snap.secureDnsHost != null && snap.privateDnsName != null ->
                context.getString(R.string.notif_both_title) to
                    context.getString(R.string.notif_both_text_secure, snap.secureDnsHost, snap.privateDnsName)
            else -> null
        }
        val title: String
        val body: String
        if (both != null) {
            title = both.first
            body = both.second
        } else {
            when {
                snap.vpnName != null -> title = context.getString(R.string.notif_vpn_active, snap.vpnName)
                snap.secureDnsHost != null ->
                    title = context.getString(R.string.notif_secure_dns_active, snap.secureDnsHost)
                else -> title = context.getString(R.string.notif_dns_active, snap.privateDnsName ?: "")
            }
            body = ""
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val turnOff = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, StatusActionReceiver::class.java).setAction(StatusActionReceiver.ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_shield)
            .setContentTitle(title)
            .setContentText(body.ifEmpty { title })
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, context.getString(R.string.notif_action_turn_off), turnOff)
        if (body.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        return builder.build()
    }

    /** Variants used by the hosting services — non-null because the service is alive. */
    fun vpnServiceNotification(context: Context): Notification =
        build(context, snapshot(context)) ?: fallback(context)

    fun secureDnsServiceNotification(context: Context): Notification =
        build(context, snapshot(context)) ?: fallback(context)

    fun statusServiceNotification(context: Context): Notification =
        build(context, snapshot(context)) ?: fallback(context)

    private fun fallback(context: Context): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_shield)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Last rendered content signature — skips redundant notification re-posts. */
    @Volatile
    private var lastSignature: String? = null

    /**
     * Re-renders the notification owned by whichever host is currently alive
     * (called after any VPN/DNS state change while a notification is showing).
     * Cheap: a snapshot signature guard means unchanged content is not re-posted.
     */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        val snap = snapshot(appContext)
        val signature = "${snap.vpnName}|${snap.secureDnsHost}|${snap.privateDnsName}"
        if (signature == lastSignature) return
        lastSignature = signature
        if (NetPilotVpnService.isRunning) {
            build(appContext, snap)?.let { nm.notify(NOTIF_ID_VPN, it) }
        }
        if (SecureDnsVpnService.runningHostname != null) {
            build(appContext, snap)?.let { nm.notify(NOTIF_ID_SECURE_DNS, it) }
        }
        if (VpnStatusService.running) {
            build(appContext, snap)?.let { nm.notify(NOTIF_ID_STATUS, it) }
        }
    }

    /**
     * Keeps the standalone status host aligned with reality:
     * runs while system Private DNS is strict and/or a platform IKEv2 session
     * is active — unless a classic service already hosts the notification.
     */
    fun reconcileStatusService(context: Context) {
        val appContext = context.applicationContext
        val dnsOn = PrivateDnsManager.read(appContext).mode == DnsMode.CUSTOM
        val platform = VpnSessionState.platformSessionActive(appContext)
        val classicHosting = NetPilotVpnService.isRunning || SecureDnsVpnService.runningHostname != null
        val shouldRun = (dnsOn || platform) && !classicHosting
        when {
            shouldRun && !VpnStatusService.running -> VpnStatusService.start(appContext)
            !shouldRun && VpnStatusService.running -> VpnStatusService.stop(appContext)
            VpnStatusService.running -> refresh(appContext)
            // A classic service hosts the notification: re-render its content so
            // DNS flips are reflected there too (never leave a stale combined view).
            classicHosting -> refresh(appContext)
        }
    }
}
