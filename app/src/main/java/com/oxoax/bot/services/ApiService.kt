package com.oxoax.bot.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // ===== 频率限制重试 =====
    private suspend fun <T> withRetry(maxRetries: Int = 3, fn: suspend () -> T): T {
        for (attempt in 0..maxRetries) {
            try {
                return fn()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isRateLimit = msg.contains("频率") || msg.contains("rate") || msg.contains("limit") || msg.contains("429")
                if (!isRateLimit || attempt == maxRetries) throw e
                val waitSec = 15 * (1 shl attempt)
                delay(waitSec * 1000L)
            }
        }
        throw Exception("重试次数已用完")
    }

    // ===== Token 管理 =====
    private var directToken: String? = null
    private var directTokenExpiry: Long = 0

    private suspend fun getDirectToken(): String {
        val now = System.currentTimeMillis()
        directToken?.let { if (now < directTokenExpiry - 60000) return it }

        val appId = LocalStore.botId ?: throw Exception("未配置机器人 AppID")
        val secret = LocalStore.botSecret ?: throw Exception("未配置机器人 Secret")

        val body = JSONObject().apply {
            put("appId", appId)
            put("clientSecret", secret)
        }

        val request = Request.Builder()
            .url("https://bots.qq.com/app/getAppAccessToken")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            val token = d.optString("access_token", "")
            if (token.isNotEmpty()) {
                directToken = token
                directTokenExpiry = now + (d.optInt("expires_in", 7200) * 1000L)
                token
            } else {
                throw Exception("获取 Token 失败: ${d.opt("errCode") ?: d.opt("code")} ${d.opt("errMsg") ?: d.opt("message")}")
            }
        }
    }

    private suspend fun apiHeaders(): Map<String, String> {
        val token = getDirectToken()
        return mapOf(
            "Authorization" to "QQBot $token",
            "Content-Type" to "application/json"
        )
    }

    // ===== 直连发送群消息 =====
    suspend fun directSendToGroup(group: String, body: Map<String, Any?>): Map<String, Any?> {
        val token = getDirectToken()
        val jsonBody = JSONObject(body).toString()
        val request = Request.Builder()
            .url("https://api.sgroup.qq.com/v2/groups/$group/messages")
            .addHeader("Authorization", "QQBot $token")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception(d.getString("message"))
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    // ===== 直连发送私聊消息 =====
    suspend fun directSendToUser(openid: String, body: Map<String, Any?>): Map<String, Any?> {
        val token = getDirectToken()
        val jsonBody = JSONObject(body).toString()
        val request = Request.Builder()
            .url("https://api.sgroup.qq.com/v2/users/$openid/messages")
            .addHeader("Authorization", "QQBot $token")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception(d.getString("message"))
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    suspend fun sendToGroup(group: String, body: Map<String, Any?>) = directSendToGroup(group, body)
    suspend fun sendToUser(openid: String, body: Map<String, Any?>) = directSendToUser(openid, body)

    // ===== 上传富媒体 =====
    suspend fun uploadMedia(group: String, url: String, fileType: Int): String? {
        val token = getDirectToken()
        val body = JSONObject().apply {
            put("file_type", fileType)
            put("url", url)
            put("srv_send_msg", false)
        }
        val request = Request.Builder()
            .url("https://api.sgroup.qq.com/v2/groups/$group/files")
            .addHeader("Authorization", "QQBot $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            d.optString("file_info", null)
        }
    }

    // ===== 获取群信息 =====
    suspend fun fetchGroupInfo(groupOpenid: String): Map<String, Any?>? {
        return try {
            val token = getDirectToken()
            val request = Request.Builder()
                .url("https://api.sgroup.qq.com/v2/groups/$groupOpenid/info")
                .addHeader("Authorization", "QQBot $token")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val d = JSONObject(response.body?.string() ?: "{}")
                d.keys().asSequence().associateWith { d.get(it) }
            }
        } catch (_: Exception) { null }
    }

    // ===== 获取消息列表 =====
    suspend fun fetchMessages(groupOpenid: String, before: String? = null, limit: Int = 20): List<Map<String, Any?>> {
        val token = getDirectToken()
        var url = "https://api.sgroup.qq.com/v2/groups/$groupOpenid/messages?limit=$limit"
        if (before != null) url += "&before=$before"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "QQBot $token")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            val messages = d.optJSONArray("messages") ?: return@withContext emptyList()
            (0 until messages.length()).map { i ->
                val obj = messages.getJSONObject(i)
                obj.keys().asSequence().associateWith { obj.get(it) }
            }
        }
    }

    // ===== 撤回消息 =====
    suspend fun recallMessage(groupOpenid: String, messageId: String): Boolean {
        val token = getDirectToken()
        val request = Request.Builder()
            .url("https://api.sgroup.qq.com/v2/groups/$groupOpenid/messages/$messageId")
            .addHeader("Authorization", "QQBot $token")
            .delete()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.code == 200 || response.code == 204
        }
    }
}
