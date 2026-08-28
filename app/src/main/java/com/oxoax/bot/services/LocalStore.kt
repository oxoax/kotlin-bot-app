package com.oxoax.bot.services

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object LocalStore {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)
    }

    // ===== Bot 配置 =====
    val botId: String? get() = prefs.getString("bot_id", null)
    val botSecret: String? get() = prefs.getString("bot_secret", null)
    val botQQ: String? get() = prefs.getString("bot_qq", null)
    val botName: String? get() = prefs.getString("bot_name", null)
    val botAvatar: String? get() = prefs.getString("bot_avatar", null)
    val isConfigured: Boolean get() = !botId.isNullOrEmpty()

    suspend fun setBotName(v: String) { prefs.edit().putString("bot_name", v).apply() }
    suspend fun setBotAvatar(v: String) { prefs.edit().putString("bot_avatar", v).apply() }
    suspend fun setBotQQ(v: String) { prefs.edit().putString("bot_qq", v).apply() }
    suspend fun setBotId(v: String) { prefs.edit().putString("bot_id", v).apply() }
    suspend fun setBotSecret(v: String) { prefs.edit().putString("bot_secret", v).apply() }

    fun saveBot(id: String, secret: String, qq: String = "", name: String = "", avatar: String = "") {
        prefs.edit().apply {
            putString("bot_id", id)
            putString("bot_secret", secret)
            if (qq.isNotEmpty()) putString("bot_qq", qq)
            if (name.isNotEmpty()) putString("bot_name", name)
            if (avatar.isNotEmpty()) putString("bot_avatar", avatar)
            apply()
        }
    }

    // ===== 群聊管理 =====
    fun getGroups(): List<Map<String, String>> {
        val raw = prefs.getString("groups", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveGroups(groups: List<Map<String, String>>) {
        val arr = JSONArray()
        groups.forEach { g ->
            val obj = JSONObject()
            g.forEach { (k, v) -> obj.put(k, v) }
            arr.put(obj)
        }
        prefs.edit().putString("groups", arr.toString()).apply()
    }

    fun addGroup(name: String, group: String, official: String,
                 groupOpenid: String = "", groupNumber: String = "", avatar: String = "") {
        val groups = getGroups().toMutableList()
        groups.removeAll { it["group"] == group }
        val m = mutableMapOf("name" to name, "group" to group, "official" to official)
        if (groupOpenid.isNotEmpty()) m["group_openid"] = groupOpenid
        if (groupNumber.isNotEmpty()) m["group_number"] = groupNumber
        if (avatar.isNotEmpty()) m["avatar"] = avatar
        groups.add(m)
        saveGroups(groups)
    }

    fun updateGroupOpenid(group: String, groupOpenid: String) {
        val groups = getGroups().toMutableList()
        for (g in groups) {
            if (g["group"] == group) {
                (g as MutableMap)["group_openid"] = groupOpenid
                break
            }
        }
        saveGroups(groups)
    }

    fun removeGroup(group: String) {
        saveGroups(getGroups().filter { it["group"] != group })
    }

    // ===== 群最后活跃时间 =====
    fun getGroupLastActive(): Map<String, Long> {
        val raw = prefs.getString("group_last_active", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (_: Exception) { emptyMap() }
    }

    fun setGroupLastActive(group: String, ts: Long) {
        val m = getGroupLastActive().toMutableMap()
        m[group] = ts
        val obj = JSONObject()
        m.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("group_last_active", obj.toString()).apply()
    }

    // ===== 私聊列表 =====
    fun getPrivateChats(): List<Map<String, String>> {
        val raw = prefs.getString("private_chats", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun savePrivateChats(chats: List<Map<String, String>>) {
        val arr = JSONArray()
        chats.forEach { c ->
            val obj = JSONObject()
            c.forEach { (k, v) -> obj.put(k, v) }
            arr.put(obj)
        }
        prefs.edit().putString("private_chats", arr.toString()).apply()
    }

    fun addPrivateChat(name: String, openid: String) {
        val chats = getPrivateChats().toMutableList()
        chats.removeAll { it["openid"] == openid }
        chats.add(mapOf("name" to name, "openid" to openid))
        savePrivateChats(chats)
    }

    fun removePrivateChat(openid: String) {
        savePrivateChats(getPrivateChats().filter { it["openid"] != openid })
    }

    // ===== 自动发现的群聊 =====
    fun getAutoGroups(): List<Map<String, String>> {
        val raw = prefs.getString("auto_groups", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveAutoGroups(groups: List<Map<String, String>>) {
        val arr = JSONArray()
        groups.forEach { g ->
            val obj = JSONObject()
            g.forEach { (k, v) -> obj.put(k, v) }
            arr.put(obj)
        }
        prefs.edit().putString("auto_groups", arr.toString()).apply()
    }

    fun addAutoGroup(name: String, groupOpenid: String, groupNumber: String = "") {
        val groups = getAutoGroups().toMutableList()
        groups.removeAll { it["group_openid"] == groupOpenid }
        groups.add(mapOf("name" to name, "group_openid" to groupOpenid, "group_number" to groupNumber))
        saveAutoGroups(groups)
    }

    // ===== 主题模式 =====
    // 0=light, 1=dark, 2=system
    var themeModeValue: Int
        get() = prefs.getInt("theme_mode", 2)
        set(value) { prefs.edit().putInt("theme_mode", value).apply() }
}
