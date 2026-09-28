package app.netpilot.core.ui

import android.content.Context
import android.content.pm.PackageManager
import app.netpilot.R

/** TV detection used across UI components. */
object TvUi {
    fun detect(context: Context): Boolean =
        context.resources.getBoolean(R.bool.is_television) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

val Context.isTelevision: Boolean
    get() = TvUi.detect(this)
