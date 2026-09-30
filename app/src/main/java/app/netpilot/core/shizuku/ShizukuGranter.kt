package app.netpilot.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Optional grant path via the Shizuku app (github.com/RikkaApps/Shizuku):
 * users who cannot run ADB can start Shizuku (wireless debugging on
 * Android 11+) and grant WRITE_SECURE_SETTINGS with one tap — no PC.
 *
 * The Shizuku API is the only third-party runtime library in the app and is
 * disclosed in Settings → Open-source licenses (MIT).
 */
object ShizukuGranter {

    const val PACKAGE_ID = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST_CODE = 4242

    /** Is the Shizuku app installed? (respects package visibility via <queries>.) */
    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_ID, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** Is the Shizuku server reachable right now (app running & bound)? */
    fun serverAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun permissionGranted(): Boolean = try {
        serverAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        // Requires a live binder; firing it against a dead server would crash.
        if (!serverAvailable()) return
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    /**
     * Runs `pm grant app.netpilot android.permission.WRITE_SECURE_SETTINGS`
     * through the Shizuku server via the bound user service (ADB-level
     * privileges). Requires the Shizuku permission to be granted already;
     * [onResult] is invoked on the main thread.
     */
    fun grantWriteSecureSettings(context: Context, onResult: (Boolean) -> Unit) {
        ShellService.grantWriteSecureSettings(context, onResult)
    }
}
