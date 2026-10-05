package app.netpilot.core.vpn

/**
 * Per-app (split) tunneling for WireGuard (v2.2.0) — pure string surgery on
 * the profile's .conf. The official wireguard-android library parses the
 * `IncludedApplications` / `ExcludedApplications` lines in the [Interface]
 * section (verified via javap: parseIncludedApplications /
 * parseExcludedApplications) and applies them natively through
 * VpnService.addAllowedApplication / disallowedApplication. No app-side
 * model changes, import/export/backup keep working untouched.
 *
 * WireGuard rule: Include and Exclude are mutually exclusive — enforcing
 * that here, never in the UI alone.
 */
object WireGuardSplitTunnel {

    enum class Mode { INCLUDE, EXCLUDE, ALL }

    data class Selection(val mode: Mode, val packages: List<String>)

    private val includeRegex = Regex("""(?i)^\s*IncludedApplications\s*=""")
    private val excludeRegex = Regex("""(?i)^\s*ExcludedApplications\s*=""")
    private val pkgRegex = Regex("""^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$""")

    /** Current selection encoded in [conf]; ALL when no lines are present. */
    fun read(conf: String): Selection {
        var include: String? = null
        var exclude: String? = null
        conf.lineSequence().forEach { line ->
            if (includeRegex.containsMatchIn(line)) include = line.substringAfter('=').trim()
            if (excludeRegex.containsMatchIn(line)) exclude = line.substringAfter('=').trim()
        }
        return when {
            include != null && exclude != null ->
                // Contradictory config — treat as ALL; the next save repairs it.
                Selection(Mode.ALL, emptyList())
            include != null -> Selection(Mode.INCLUDE, split(include!!))
            exclude != null -> Selection(Mode.EXCLUDE, split(exclude!!))
            else -> Selection(Mode.ALL, emptyList())
        }
    }

    /**
     * Returns the conf with the selection applied, or null when the
     * selection itself is invalid (a package list that is empty, contains
     * a malformed package name, or a conf without an [Interface] section).
     */
    fun apply(conf: String, selection: Selection): String? {
        if (!conf.contains("[Interface]", ignoreCase = true)) return null
        val pkgs = selection.packages.map { it.trim() }.filter { it.isNotEmpty() }
        when (selection.mode) {
            Mode.ALL -> Unit
            else -> {
                if (pkgs.isEmpty()) return null
                if (pkgs.any { !pkgRegex.matches(it) }) return null
            }
        }
        val stripped = conf.lineSequence()
            .filterNot { includeRegex.containsMatchIn(it) || excludeRegex.containsMatchIn(it) }
            .joinToString("\n")
        val lines = stripped.split("\n").toMutableList()
        if (selection.mode != Mode.ALL) {
            val key = if (selection.mode == Mode.INCLUDE) "IncludedApplications" else "ExcludedApplications"
            val insertAt = lines.indexOfFirst { it.trim().startsWith("[Peer]", ignoreCase = true) }
                .let { if (it >= 0) it else lines.size }
            lines.add(insertAt, "$key = ${pkgs.joinToString(", ")}")
        }
        return lines.joinToString("\n")
    }

    private fun split(raw: String): List<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
