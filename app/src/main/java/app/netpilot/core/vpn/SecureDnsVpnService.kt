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
import app.netpilot.core.dns.DnsMessage
import app.netpilot.core.dns.DotClient
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Zero-setup Secure DNS: a local VPN tunnel whose only purpose is DNS.
 *
 * The TUN gets a single /32 address which is also registered as the system DNS
 * server — so every DNS lookup flows through this service and is re-sent via
 * first-party DNS-over-TLS ([DotClient]) to the user's selected provider.
 * No other traffic enters the tunnel; no WRITE_SECURE_SETTINGS (no ADB) needed —
 * just the standard Android VPN consent dialog, operable by remote control.
 *
 * The upstream hostname is resolved *before* the tunnel is established
 * (bootstrap) so the DoT connection can never recurse into the tunnel itself.
 */
class SecureDnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var queryPool: java.util.concurrent.ExecutorService? = null
    private var readerThread: Thread? = null
    private var providerHost: String? = null
    private var bootstrapAddresses: List<InetAddress> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST)
                if (host.isNullOrBlank()) {
                    stopTunnel()
                } else {
                    goForeground(host)
                    startTunnel(host)
                }
                return START_STICKY
            }
            else -> {
                stopTunnel()
                return START_NOT_STICKY
            }
        }
    }

    private fun startTunnel(host: String) {
        // 1) Bootstrap-resolve the DoT provider while the system DNS is untouched.
        val resolved = runCatching {
            InetAddress.getAllByName(host).toList().filterNotNull()
        }.getOrDefault(emptyList())
        if (resolved.isEmpty()) {
            stopTunnel()
            return
        }
        bootstrapAddresses = resolved
        providerHost = host

        // 2) Bring up the capture tunnel.
        val descriptor = establish() ?: run {
            stopTunnel()
            return
        }
        tun = descriptor
        output = FileOutputStream(descriptor.fileDescriptor)
        runningHostname = host

        // 3) Reader + small upstream query pool.
        val pool = Executors.newFixedThreadPool(QUERY_THREADS)
        queryPool = pool
        readerThread = Thread({
            val input = FileInputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val n = input.read(buffer)
                    if (n < 1) break
                    PacketOps.parseDnsUdpV4(buffer, n)?.let { query ->
                        pool.execute { answer(query, host) }
                    }
                }
            } catch (_: Exception) {
                // TUN closed or read error — fall through to shutdown.
            }
        }, "netpilot-dns-reader")
        readerThread?.start()
    }

    private fun answer(query: PacketOps.DnsQueryInfo, host: String) {
        val id = DnsMessage.parseId(query.dns)
        val response = if (id == null || DnsMessage.questionName(query.dns) == null) {
            null
        } else {
            DotClient(host, bootstrapAddresses.firstOrNull()).query(query.dns)
                ?: DnsMessage.servFailResponse(query.dns)
        } ?: return

        val packet = PacketOps.buildUdpV4Response(query, response)
        val out = output ?: return
        synchronized(out) {
            try {
                out.write(packet)
                out.flush()
            } catch (_: Exception) {
                // TUN gone — reader loop will shut the service down.
            }
        }
    }

    private fun establish(): ParcelFileDescriptor? = try {
        Builder()
            .setSession(getString(R.string.secure_dns_session))
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(TUN_ADDRESS)
            .addRoute(TUN_ADDRESS, 32)
            .setMtu(DEFAULT_MTU)
            .establish()
    } catch (_: Exception) {
        null
    }

    private fun stopTunnel() {
        runningHostname = null
        providerHost = null
        readerThread?.interrupt()
        readerThread = null
        queryPool?.let { pool ->
            pool.shutdown()
            runCatching { pool.awaitTermination(1, TimeUnit.SECONDS) }
            pool.shutdownNow()
        }
        queryPool = null
        runCatching { output?.flush() }
        output = null
        runCatching { tun?.close() }
        tun = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(provider: String) {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_shield)
            .setContentTitle(getString(R.string.secure_dns_session))
            .setContentText(getString(R.string.secure_dns_notification, provider))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("InlinedApi")
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.secure_dns_session),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.netpilot.action.SECURE_DNS_START"
        const val ACTION_STOP = "app.netpilot.action.SECURE_DNS_STOP"
        const val EXTRA_HOST = "app.netpilot.extra.SECURE_DNS_HOST"

        /** Reflects the live state so UI can render without polling the system. */
        @Volatile
        var runningHostname: String? = null
            private set

        private const val CHANNEL_ID = "netpilot_secure_dns"
        private const val NOTIFICATION_ID = 43
        private const val TUN_ADDRESS = "10.111.3.1"
        private const val DEFAULT_MTU = 1500
        private const val BUFFER_SIZE = 32_768
        private const val QUERY_THREADS = 4
    }
}
