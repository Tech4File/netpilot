package app.netpilot.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

/**
 * The Shizuku "user service": a tiny runner executed inside the Shizuku server
 * (ADB-level privileges) so NetPilot can self-grant WRITE_SECURE_SETTINGS with
 * one tap — no PC, no root.
 */
class ShellService : IShellService.Stub() {

    override fun run(command: Array<out String>?): Int = try {
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()
    } catch (_: Throwable) {
        -1
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L

        @Volatile
        private var shell: IShellService? = null

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                shell = IShellService.Stub.asInterface(binder)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shell = null
            }
        }

        fun grantWriteSecureSettings(context: Context, onResult: (Boolean) -> Unit) {
            fun attempt(): Boolean = try {
                shell?.run(arrayOf("pm", "grant", "app.netpilot", "android.permission.WRITE_SECURE_SETTINGS")) == 0
            } catch (_: Throwable) {
                false
            }

            if (attempt()) {
                onResult(true)
                return
            }
            val appContext = context.applicationContext
            val args = Shizuku.UserServiceArgs(ComponentName(appContext, ShellService::class.java))
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1)
            var settled = false
            val main = Handler(Looper.getMainLooper())
            fun settleOnce(result: Boolean) {
                if (settled) return
                settled = true
                main.post { onResult(result) }
            }
            main.postDelayed({ settleOnce(false) }, TIMEOUT_MS)
            Shizuku.bindUserService(args, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    shell = IShellService.Stub.asInterface(binder)
                    settleOnce(attempt())
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shell = null
                }
            })
        }

    }
}
