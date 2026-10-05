package app.netpilot.core.tiles

/**
 * Pure state model for the VPN Quick Settings tile (v2.2.0) — the second
 * tile on the seam the DNS tile built. Same rules as DNS: the TileService is
 * boundaryless (bound only while the shade shows it), ALL decisions are made
 * here so they stay unit-testable without Android.
 */
object VpnTileState {

    enum class Action {
        /** An embedded WireGuard tunnel is up — tap disconnects. */
        TOGGLE_OFF,

        /** A last-used WireGuard profile exists — tap reconnects it. */
        CONNECT_LAST,

        /** Nothing NetPilot can toggle itself — tap opens the app. */
        OPEN_APP,
    }

    data class Model(
        val active: Boolean,
        val subtitle: String?,
        val action: Action,
    )

    /**
     * @param running        [WireGuardRuntime.isRunning] — the in-process
     *                       engine marker; when the app process dies the
     *                       userspace tunnel dies with it, so this is honest.
     * @param hasWgProfile   a usable WireGuard profile exists to reconnect.
     * @param consentGranted VpnService.prepare(context) == null.
     */
    fun compute(running: Boolean, hasWgProfile: Boolean, consentGranted: Boolean): Model = when {
        running ->
            Model(active = true, subtitle = null, action = Action.TOGGLE_OFF)
        hasWgProfile && consentGranted ->
            Model(active = false, subtitle = null, action = Action.CONNECT_LAST)
        else ->
            Model(active = false, subtitle = null, action = Action.OPEN_APP)
    }
}
