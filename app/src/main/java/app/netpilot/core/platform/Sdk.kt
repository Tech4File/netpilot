package app.netpilot.core.platform

import android.os.Build

/**
 * Central API-level gates (v2.2.0). NEW code gates here, so a future Android
 * release is adapted in ONE place: add the constant, use it, and the
 * behavior-change checklist in docs/ANDROID-VERSIONS.md covers the rest.
 *
 * Floor policy: the app supports Android 9 (API 28) → latest. Every feature
 * carries its own floor via these gates — no device is left behind and no
 * new-API call is made unguarded (lint NewApi enforces).
 */
object Sdk {
    const val P = 28 // Android 9  (minSdk — the floor everything must run on)
    const val Q = 29 // Android 10 (Tile.setSubtitle)
    const val R = 30 // Android 11 (Wireless debugging / Shizuku pairing era)
    const val S = 31 // Android 12 (Bluetooth permission split, pending rather)
    const val T = 33 // Android 13 (POST_NOTIFICATIONS runtime prompt)
    const val U = 34 // Android 14 (TileService.startActivityAndCollapse(PendingIntent), FGS types)
    const val V = 35 // Android 15 (edge-to-edge enforced)
    const val CURRENT_COMPILE = 36 // Android 16 — compileSdk

    /** Named gate — preferred over raw SDK_INT comparisons in new code. */
    fun atLeast(api: Int): Boolean = Build.VERSION.SDK_INT >= api
}
