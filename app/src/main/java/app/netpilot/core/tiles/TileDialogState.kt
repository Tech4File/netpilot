package app.netpilot.core.tiles

import app.netpilot.core.model.DnsProfile
import app.netpilot.core.model.DnsMode

/**
 * Pure state model for the tile pop-up (the dialog activity the DNS tile
 * opens). ALL decisions live here so they stay unit-testable without
 * Android — same rule as [DnsTileState] and [VpnTileState].
 */
object TileDialogState {

    data class ProfileRow(
        val id: String,
        val name: String,
        val hostname: String,
        val active: Boolean,
    )

    data class Model(
        /** The on/off switch position: Private DNS custom mode engaged. */
        val switchOn: Boolean,
        /** The switch can be turned ON (a profile exists to re-apply). */
        val canTurnOn: Boolean,
        /** Active profile name when ON, else null (drives the status line). */
        val activeProfileName: String?,
        val rows: List<ProfileRow>,
    )

    fun compute(mode: DnsMode, profiles: List<DnsProfile>, activeId: String?): Model {
        val custom = mode == DnsMode.CUSTOM
        val active = profiles.firstOrNull { it.id == activeId }
        return Model(
            switchOn = custom,
            canTurnOn = active != null,
            activeProfileName = if (custom) active?.name else null,
            rows = profiles.map {
                ProfileRow(it.id, it.name, it.hostname, custom && it.id == activeId)
            },
        )
    }
}
