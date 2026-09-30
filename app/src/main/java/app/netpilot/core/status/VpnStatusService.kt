package app.netpilot.core.status

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Standalone foreground host for the unified status notification — used when
 * no classic VPN service exists to host it (platform IKEv2 sessions and/or
 * strict system Private DNS). Runs only while something is actually active;
 * [StatusNotifications.reconcileStatusService] starts/stops it as state flips.
 */
class VpnStatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        // targetSdk 34+ requires a typed FGS; specialUse is declared in the manifest.
        val type = if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("InlinedApi") // constant inlined at compile time, guarded here
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            StatusNotifications.NOTIF_ID_STATUS,
            StatusNotifications.statusServiceNotification(this),
            type,
        )
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.netpilot.action.STATUS_START"
        const val ACTION_STOP = "app.netpilot.action.STATUS_STOP"

        /** Reflects whether the standalone status host is alive in this process. */
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            // Defensive: a background-start ban must never crash a refresh path.
            runCatching {
                context.startService(
                    Intent(context, VpnStatusService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VpnStatusService::class.java))
        }
    }
}
