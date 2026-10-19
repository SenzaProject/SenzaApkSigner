package com.senza.apksigner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Repo(ctx: Context) {
    private val sp = ctx.getSharedPreferences("senza_signer", Context.MODE_PRIVATE)

    var selectedId: String?
        get() = sp.getString("sel", null)
        set(v) { sp.edit().putString("sel", v).apply() }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    fun loadProfiles(): List<KeyProfile> = try {
        val a = JSONArray(sp.getString("profiles", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            KeyProfile(
                id = o.getString("id"), name = o.getString("name"), file = o.getString("file"),
                type = o.getString("type"), alias = o.getString("alias"),
                storePassEnc = o.str("sp"), keyPassEnc = o.str("kp"),
                subject = o.optString("subject"), sha256 = o.optString("sha256"),
                sha1 = o.optString("sha1"), md5 = o.optString("md5"),
                validTo = o.optLong("validTo"), algo = o.optString("algo"),
                createdAt = o.optLong("createdAt")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveProfiles(list: List<KeyProfile>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("file", p.file); put("type", p.type)
                put("alias", p.alias); put("sp", p.storePassEnc ?: JSONObject.NULL)
                put("kp", p.keyPassEnc ?: JSONObject.NULL)
                put("subject", p.subject); put("sha256", p.sha256); put("sha1", p.sha1)
                put("md5", p.md5); put("validTo", p.validTo); put("algo", p.algo)
                put("createdAt", p.createdAt)
            })
        }
        sp.edit().putString("profiles", a.toString()).apply()
    }

    fun loadHistory(): List<HistoryItem> = try {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryItem(
                o.getString("id"), o.optString("apkName"), o.optString("pkg"), o.optString("version"),
                o.getString("path"), o.optLong("size"), o.optLong("time"),
                o.optString("profileName"), o.optString("schemes")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(list: List<HistoryItem>) {
        val a = JSONArray()
        list.take(200).forEach { h ->
            a.put(JSONObject().apply {
                put("id", h.id); put("apkName", h.apkName); put("pkg", h.pkg); put("version", h.version)
                put("path", h.path); put("size", h.size); put("time", h.time)
                put("profileName", h.profileName); put("schemes", h.schemes)
            })
        }
        sp.edit().putString("history", a.toString()).apply()
    }

    fun loadOptions(): SignOptions {
        val d = SignOptions()
        return SignOptions(
            v1 = sp.getBoolean("o_v1", d.v1), v2 = sp.getBoolean("o_v2", d.v2),
            v3 = sp.getBoolean("o_v3", d.v3), v4 = sp.getBoolean("o_v4", d.v4),
            zipAlign = sp.getBoolean("o_align", d.zipAlign),
            pageAlign16k = sp.getBoolean("o_16k", d.pageAlign16k),
            minSdk = sp.getInt("o_min", -1).takeIf { it > 0 },
            verifyAfter = sp.getBoolean("o_verify", d.verifyAfter),
            autoSave = sp.getBoolean("o_auto", d.autoSave),
            suffix = sp.getString("o_suffix", d.suffix) ?: d.suffix
        )
    }

    fun saveOptions(o: SignOptions) {
        sp.edit()
            .putBoolean("o_v1", o.v1).putBoolean("o_v2", o.v2).putBoolean("o_v3", o.v3)
            .putBoolean("o_v4", o.v4).putBoolean("o_align", o.zipAlign)
            .putBoolean("o_16k", o.pageAlign16k).putInt("o_min", o.minSdk ?: -1)
            .putBoolean("o_verify", o.verifyAfter).putBoolean("o_auto", o.autoSave)
            .putString("o_suffix", o.suffix)
            .apply()
    }
}
