package app.netpilot.core.dns

import android.content.Context
import app.netpilot.core.model.DnsProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * On-device store of DNS profiles + which single profile is active.
 * Storage: app-private SharedPreferences as JSON — excluded from platform backups.
 */
class DnsProfileRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cache: List<DnsProfile>? = null

    fun list(): List<DnsProfile> = load()

    fun get(id: String): DnsProfile? = load().firstOrNull { it.id == id }

    /** Active profile id, or null when nothing is selected (or the selected profile was deleted). */
    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun activeProfile(): DnsProfile? = activeId()?.let { get(it) }

    fun add(name: String, hostname: String): DnsProfile {
        val profile = DnsProfile(UUID.randomUUID().toString(), name.trim(), hostname.trim())
        val updated = load() + profile
        persist(updated)
        return profile
    }

    fun update(profile: DnsProfile) {
        persist(load().map { if (it.id == profile.id) profile else it })
    }

    /** @return true when the deleted profile was the active one. */
    fun delete(id: String): Boolean {
        val wasActive = activeId() == id
        persist(load().filterNot { it.id == id })
        if (wasActive) prefs.edit().remove(KEY_ACTIVE).apply()
        return wasActive
    }

    /**
     * Activates exactly one profile. Any previous active profile is deactivated —
     * the single-active invariant the product promises.
     */
    fun setActive(id: String) {
        require(load().any { it.id == id }) { "Unknown profile: $id" }
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun clearActive() = prefs.edit().remove(KEY_ACTIVE).apply()

    /** JSON for backup/export (Settings → Export profiles). */
    fun exportJson(): String {
        val root = JSONObject()
        root.put("type", "netpilot.dns.v1")
        val arr = JSONArray()
        load().forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("hostname", p.hostname))
        }
        root.put("profiles", arr)
        root.put("active", activeId() ?: JSONObject.NULL)
        return root.toString(2)
    }

    /** Merges profiles from an export file. @return number of imported (new) profiles. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        if (root.optString("type") != "netpilot.dns.v1") return 0
        val arr = root.optJSONArray("profiles") ?: return 0
        val existing = load().toMutableList()
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            val hostname = o.optString("hostname")
            if (name.isBlank() || !HostnameValidator.isValid(hostname)) continue
            if (existing.any { it.hostname == hostname && it.name == name }) continue
            existing += DnsProfile(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                hostname = hostname,
            )
            added++
        }
        if (added > 0) persist(existing)
        return added
    }

    @Synchronized
    private fun load(): List<DnsProfile> {
        cache?.let { return it }
        val raw = prefs.getString(KEY_PROFILES, null)
        val profiles = mutableListOf<DnsProfile>()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val host = o.optString("hostname")
                    if (id.isNotBlank() && name.isNotBlank() && host.isNotBlank()) {
                        profiles += DnsProfile(id, name, host)
                    }
                }
            }
        }
        cache = profiles
        return profiles
    }

    @Synchronized
    private fun persist(profiles: List<DnsProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("hostname", p.hostname))
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
        cache = profiles
    }

    companion object {
        private const val PREFS = "netpilot_profiles"
        private const val KEY_PROFILES = "dns_profiles"
        private const val KEY_ACTIVE = "dns_active_id"
    }
}
