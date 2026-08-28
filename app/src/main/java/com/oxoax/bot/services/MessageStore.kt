package com.oxoax.bot.services

import android.content.Context
import com.oxoax.bot.models.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MessageStore {
    private lateinit var filesDir: File

    fun init(context: Context) {
        filesDir = context.filesDir
    }

    private fun msgFile(groupId: String) = File(filesDir, "msg_$groupId.json")

    private fun chatMessageToJson(msg: ChatMessage): JSONObject {
        val obj = JSONObject()
        obj.put("time", msg.time)
        obj.put("type", msg.type)
        obj.put("username", msg.username ?: "")
        obj.put("userId", msg.userId ?: "")
        obj.put("content", msg.content ?: "")
        obj.put("isAt", msg.isAt)
        obj.put("isSelf", msg.isSelf)
        obj.put("role", msg.role)
        obj.put("isBot", msg.isBot)
        msg.sentAt?.let { obj.put("sentAt", it) }
        obj.put("messageId", msg.messageId ?: "")
        obj.put("msgIdx", msg.msgIdx ?: "")
        msg.attachments?.let { obj.put("attachments", JSONArray(it)) }
        msg.buttonData?.let { obj.put("buttonData", JSONObject(it)) }
        msg.quote?.let { obj.put("quote", JSONObject(it)) }
        msg.rawPayload?.let { obj.put("rawPayload", JSONObject(it)) }
        return obj
    }

    private fun jsonToChatMessage(m: JSONObject): ChatMessage {
        fun optStr(key: String): String? = if (m.has(key) && !m.isNull(key)) m.optString(key) else null
        return ChatMessage(
            time = m.optString("time", ""),
            type = m.optString("type", ""),
            username = optStr("username"),
            userId = optStr("userId"),
            content = optStr("content"),
            isAt = m.optBoolean("isAt", false),
            isSelf = m.optBoolean("isSelf", false),
            role = m.optString("role", "member"),
            isBot = m.optBoolean("isBot", false),
            sentAt = if (m.has("sentAt") && !m.isNull("sentAt")) m.optLong("sentAt") else null,
            messageId = optStr("messageId"),
            msgIdx = optStr("msgIdx"),
            attachments = if (m.has("attachments") && !m.isNull("attachments"))
                m.optJSONArray("attachments")?.let { arr -> (0 until arr.length()).map { arr.get(it) } }
            else null,
            buttonData = if (m.has("buttonData") && !m.isNull("buttonData"))
                m.optJSONObject("buttonData")?.let { obj -> obj.keys().asSequence().associateWith { obj.get(it) } }
            else null,
            quote = if (m.has("quote") && !m.isNull("quote"))
                m.optJSONObject("quote")?.let { obj -> obj.keys().asSequence().associateWith { obj.get(it) } }
            else null,
            rawPayload = if (m.has("rawPayload") && !m.isNull("rawPayload"))
                m.optJSONObject("rawPayload")?.let { obj -> obj.keys().asSequence().associateWith { obj.get(it) } }
            else null
        )
    }

    fun appendMessage(groupId: String, msg: ChatMessage) {
        try {
            val file = msgFile(groupId)
            val existing = mutableListOf<ChatMessage>()
            if (file.exists()) {
                try {
                    val arr = JSONArray(file.readText())
                    existing.addAll((0 until arr.length()).map { jsonToChatMessage(arr.getJSONObject(it)) })
                } catch (_: Exception) {}
            }
            // 去重
            if (!msg.messageId.isNullOrEmpty()) {
                if (existing.any { it.messageId == msg.messageId }) return
            }
            existing.add(msg)
            val arr = JSONArray()
            existing.forEach { arr.put(chatMessageToJson(it)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMessages(groupId: String): List<ChatMessage> {
        return try {
            val file = msgFile(groupId)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { jsonToChatMessage(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun merge(local: List<ChatMessage>, remote: List<ChatMessage>): List<ChatMessage> {
        val map = mutableMapOf<String, ChatMessage>()
        for (m in remote) {
            val key = if (!m.messageId.isNullOrEmpty()) "mid:${m.messageId}" else "${m.time}_${m.userId}_${m.content}"
            map[key] = m
        }
        for ((i, m) in local.withIndex()) {
            if (m.isSelf && !m.messageId.isNullOrEmpty()) {
                map["mid:${m.messageId}"] = m
            } else if (m.isSelf) {
                map["self_${m.time}_${m.content}_$i"] = m
            } else {
                val key = if (!m.messageId.isNullOrEmpty()) "mid:${m.messageId}" else "${m.time}_${m.userId}_${m.content}"
                if (!map.containsKey(key)) map[key] = m
            }
        }
        return map.values.sortedBy { it.time }
    }

    fun clearMessages(groupId: String) {
        try { msgFile(groupId).delete() } catch (_: Exception) {}
    }
}
