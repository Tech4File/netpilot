package app.netpilot.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.netpilot.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Shizuku "user service": a tiny runner executed inside the Shizuku server
 * (ADB-level privileges) so NetPilot can self-grant WRITE_SECURE_SETTINGS with
 * one tap — no PC, no root.
 */
class ShellService : IShellService.Stub() {

    override fun run(command: Array<out String>?): Int = try {
        val process = Runtime.getRuntime().exec(command)
        try {
            // Drain both pipes (a chatty command must never block on a full
            // pipe buffer), then reap the process.
            process.inputStream.use { it.readBytes() }
            process.errorStream.use { it.readBytes() }
            process.waitFor()
        } finally {
            runCatching { process.destroy() }
        }
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
            val appContext = context.applicationContext
            // ApplicationId comes from the build, never hard-coded; the constant
            // is inlined at compile time so it is valid inside the Shizuku
            // server process too.
            val command = arrayOf(
                "pm", "grant", BuildConfig.APPLICATION_ID,
                "android.permission.WRITE_SECURE_SETTINGS",
            )
            val settled = AtomicBoolean(false)
            val main = Handler(Looper.getMainLooper())
            fun settleOnce(result: Boolean) {
                if (!settled.compareAndSet(false, true)) return
                main.post { onResult(result) }
            }
            fun attempt(): Boolean = try {
                shell?.run(command) == 0
            } catch (_: Throwable) {
                false
            }

            main.postDelayed({ settleOnce(false) }, TIMEOUT_MS)
            // IPC and process exec run on a worker — never on the caller
            // (main) thread; the result is marshalled back to the main looper.
            Thread {
                if (attempt()) {
                    settleOnce(true)
                    return@Thread
                }
                val args = Shizuku.UserServiceArgs(ComponentName(appContext, ShellService::class.java))
                    .processNameSuffix("shell")
                    .debuggable(false)
                    .version(1)
                Shizuku.bindUserService(args, object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        shell = IShellService.Stub.asInterface(binder)
                        Thread { settleOnce(attempt()) }.start()
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        shell = null
                    }
                })
            }.start()
        }

    }
}
