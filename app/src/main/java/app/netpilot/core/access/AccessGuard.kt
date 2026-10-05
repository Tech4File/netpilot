package app.netpilot.core.access

/**
 * Access-lifecycle state machine (v2.2.0): answers "can NetPilot change the
 * system Private DNS setting right now, and if not, WHY — with the exact
 * recovery path" — including the TV power-cycle cases.
 *
 * Field facts (docs/ACCESS-PERSISTENCE.md):
 *  - The `adb pm grant WRITE_SECURE_SETTINGS` grant is stored by the package
 *    manager and SURVIVES TV power off/on, restarts and same-signature app
 *    updates. It disappears only on uninstall, "Clear data" or factory reset.
 *  - The Private DNS setting itself is a system secure setting — once set it
 *    keeps protecting across restarts even if the app is never opened again.
 *  - Shizuku (the alternate path) is a PROCESS: it dies on every power
 *    cycle. After a restart the user must open Shizuku and tap Start again
 *    (pairing persists). Until then Shizuku actions are unavailable.
 *
 * So: a restarted TV normally needs NOTHING from us. But if access is gone
 * anyway (factory reset, clear data, or a Shizuku-only setup that has not
 * been restarted yet), the app must re-prompt exactly like the very first
 * launch — that is [AccessStatus.REGRANT], and Dashboard acts on it.
 */
object AccessGuard {

    enum class AccessStatus {
        /** WRITE_SECURE_SETTINGS granted — full control, nothing to do. */
        READY,

        /** No adb grant, but Shizuku is up and permitted — usable path. */
        SHIZUKU_READY,

        /**
         * Neither path usable AND the user has completed (or been offered)
         * setup before — re-show the permission guide with the "access lost"
         * explanation, exactly like first launch.
         */
        REGRANT,

        /** True first launch — the normal onboarding guide. */
        ONBOARD,
    }

    data class Inputs(
        val adbGrant: Boolean,
        val shizukuUsable: Boolean,
        /** The first-launch guide was shown at least once before. */
        val setupGuideShown: Boolean,
        /** Any access path worked at some point (grant or Shizuku). */
        val accessHeldBefore: Boolean,
    )

    fun evaluate(x: Inputs): AccessStatus = when {
        x.adbGrant -> AccessStatus.READY
        x.shizukuUsable -> AccessStatus.SHIZUKU_READY
        x.accessHeldBefore || x.setupGuideShown -> AccessStatus.REGRANT
        else -> AccessStatus.ONBOARD
    }
}
