package app.netpilot.core.tiles

import app.netpilot.core.model.DnsMode

/**
 * Pure state model for the NetPilot DNS Quick Settings tile — unit-testable
 * without a device. The tile is BOUNDARYLESS: the system binds it only while
 * it is visible/clicked, so it costs nothing when not in use (battery model,
 * see docs/PERFORMANCE.md).
 */
object DnsTileState {

    data class Model(
        val active: Boolean,
        val subtitle: String?,
        val clickable: Boolean,
    )

    fun compute(hasPermission: Boolean, mode: DnsMode, profileName: String?): Model = when {
        !hasPermission -> Model(active = false, subtitle = null, clickable = false)
        mode == DnsMode.CUSTOM ->
            Model(active = true, subtitle = profileName?.takeIf { it.isNotBlank() }, clickable = true)
        else -> Model(active = false, subtitle = null, clickable = true)
    }
}
