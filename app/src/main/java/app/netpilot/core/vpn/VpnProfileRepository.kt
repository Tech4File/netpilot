package app.netpilot.core.vpn

import android.content.Context
import app.netpilot.core.model.VpnAuthType
import app.netpilot.core.model.VpnProfile
import app.netpilot.core.model.VpnType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** On-device store of VPN profiles (app-private, excluded from platform backups). */
class VpnProfileRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cache: List<VpnProfile>? = null

    fun list(): List<VpnProfile> = load()

    fun get(id: String): VpnProfile? = load().firstOrNull { it.id == id }

    fun add(profile: VpnProfile): VpnProfile {
        val stored = profile.copy(id = UUID.randomUUID().toString())
        persist(load() + stored)
        return stored
    }

    fun update(profile: VpnProfile) {
        persist(load().map { if (it.id == profile.id) profile else it })
    }

    fun delete(id: String): Boolean {
        val existed = load().any { it.id == id }
        persist(load().filterNot { it.id == id })
        return existed
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("type", "netpilot.vpn.v1")
        val arr = JSONArray()
        load().forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("serverHost", p.serverHost)
                    .put("serverPort", p.serverPort)
                    .put("authType", p.authType.name)
                    .put("username", p.username)
                    .put("caCertPem", p.caCertPem ?: JSONObject.NULL)
                    .put("ovpnSummary", p.ovpnSummary ?: JSONObject.NULL)
            )
        }
        root.put("profiles", arr)
        return root.toString(2)
    }

    /** Merges metadata (never secrets) from an export file. @return imported count. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        if (root.optString("type") != "netpilot.vpn.v1") return 0
        val arr = root.optJSONArray("profiles") ?: return 0
        val existing = load().toMutableList()
        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            val host = o.optString("serverHost")
            if (name.isBlank() || host.isBlank()) continue
            if (existing.any { it.name == name && it.serverHost == host }) continue
            existing += VpnProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                type = runCatching { VpnType.valueOf(o.optString("type")) }.getOrDefault(VpnType.PLATFORM_IKEV2),
                serverHost = host,
                serverPort = o.optInt("serverPort", 1194),
                authType = runCatching { VpnAuthType.valueOf(o.optString("authType")) }.getOrDefault(VpnAuthType.USER_PASS),
                username = o.optString("username"),
                caCertPem = o.optString("caCertPem").takeIf { it.isNotBlank() },
                ovpnSummary = o.optString("ovpnSummary").takeIf { it.isNotBlank() },
            )
            added++
        }
        if (added > 0) persist(existing)
        return added
    }

    /** Id of the profile we last asked the system to connect (UI hints only). */
    fun lastConnectedId(): String? = prefs.getString(KEY_LAST_CONNECTED, null)

    fun setLastConnectedId(id: String?) {
        if (id == null) prefs.edit().remove(KEY_LAST_CONNECTED).apply()
        else prefs.edit().putString(KEY_LAST_CONNECTED, id).apply()
    }

    @Synchronized
    private fun load(): List<VpnProfile> {
        cache?.let { return it }
        val raw = prefs.getString(KEY_PROFILES, null)
        val profiles = mutableListOf<VpnProfile>()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val name = o.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    profiles += VpnProfile(
                        id = id,
                        name = name,
                        type = runCatching { VpnType.valueOf(o.optString("type")) }.getOrDefault(VpnType.PLATFORM_IKEV2),
                        serverHost = o.optString("serverHost"),
                        serverPort = o.optInt("serverPort", 1194),
                        authType = runCatching { VpnAuthType.valueOf(o.optString("authType")) }.getOrDefault(VpnAuthType.USER_PASS),
                        username = o.optString("username"),
                        password = o.optString("password"),
                        preSharedKey = o.optString("preSharedKey"),
                        caCertPem = o.optString("caCertPem").takeIf { it.isNotBlank() },
                        ovpnConfig = o.optString("ovpnConfig").takeIf { it.isNotBlank() },
                        ovpnSummary = o.optString("ovpnSummary").takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        cache = profiles
        return profiles
    }

    @Synchronized
    private fun persist(profiles: List<VpnProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("serverHost", p.serverHost)
                    .put("serverPort", p.serverPort)
                    .put("authType", p.authType.name)
                    .put("username", p.username)
                    .put("password", p.password)
                    .put("preSharedKey", p.preSharedKey)
                    .put("caCertPem", p.caCertPem ?: JSONObject.NULL)
                    .put("ovpnConfig", p.ovpnConfig ?: JSONObject.NULL)
                    .put("ovpnSummary", p.ovpnSummary ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
        cache = profiles
    }

    companion object {
        private const val PREFS = "netpilot_profiles"
        private const val KEY_PROFILES = "vpn_profiles"
        const val KEY_LAST_CONNECTED = "vpn_last_connected_id"
    }
}
