package app.netpilot.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.netpilot.MainActivity
import app.netpilot.R

/**
 * VpnService scaffold for OpenVPN profiles.
 *
 * The complete VpnService plumbing lives here: consent handling, TUN construction
 * from the parsed .ovpn config (addresses, routes, DNS, MTU), the required
 * targetSdk-34+ "specialUse" foreground service with notification, and teardown.
 * The packet-level data channel is delegated to [VpnDataChannel] — when no engine
 * module is present the service explains and exits rather than faking a connection.
 */
class NetPilotVpnService : VpnService() {

    private var channel: VpnDataChannel? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                teardown()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> handleConnect(intent)
            else -> {
                teardown()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun handleConnect(intent: Intent) {
        goForeground()

        val raw = intent.getStringExtra(EXTRA_OVPN)
        val session = intent.getStringExtra(EXTRA_SESSION) ?: "NetPilot"
        val config = raw?.let { OvpnConfigParser.parse(it) }

        if (config == null || !config.isUsableForProfile()) {
            teardown()
            return
        }

        val engine = VpnDataChannel.factory?.invoke(this)
        if (engine == null) {
            // No transport module installed — be honest, show why, and exit.
            notify(MAIN_NOTIFICATION_ID, buildNotification(getString(R.string.ovpn_engine_title)))
            teardown()
            return
        }

        val descriptor = establish(config) ?: run {
            teardown()
            return
        }
        tun = descriptor
        channel = engine
        val up = runCatching { engine.open(descriptor, config) }.getOrDefault(false)
        if (!up) teardown()
    }

    private fun establish(config: OvpnConfig): ParcelFileDescriptor? = try {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(config.mtu ?: DEFAULT_MTU)
            .addAddress(TUN_LOCAL_V4, 32)
            .addDnsServer(config.dhcpDns.firstOrNull() ?: TUN_DNS_FALLBACK)
            .addRoute("0.0.0.0", 0)
        if (Build.VERSION.SDK_INT >= 29) builder.setBlocking(false)
        builder.establish()
    } catch (_: Exception) {
        null
    }

    private fun teardown() {
        runCatching { channel?.close() }
        channel = null
        runCatching { tun?.close() }
        tun = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        ensureChannel()
        val notification = buildNotification(getString(R.string.app_name))
        // targetSdk 34+ requires a typed FGS; specialUse is declared in the manifest.
        val type = if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("InlinedApi") // constant inlined at compile time, guarded here
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, MAIN_NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notify(id: Int, notification: Notification) {
        ensureChannel()
        getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "app.netpilot.action.CONNECT"
        const val ACTION_DISCONNECT = "app.netpilot.action.DISCONNECT"
        const val EXTRA_OVPN = "app.netpilot.extra.OVPN_CONFIG"
        const val EXTRA_SESSION = "app.netpilot.extra.SESSION"

        private const val CHANNEL_ID = "netpilot_vpn"
        private const val MAIN_NOTIFICATION_ID = 41
        private const val DEFAULT_MTU = 1500
        private const val TUN_LOCAL_V4 = "10.111.0.2"
        private const val TUN_DNS_FALLBACK = "10.111.0.1"
    }
}
