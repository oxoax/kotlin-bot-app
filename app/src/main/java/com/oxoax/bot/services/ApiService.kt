package com.oxoax.bot.services

import android.util.Log
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
    private const val TAG = "ApiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    // Token
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
            val responseBody = response.body?.string() ?: "{}"
            val d = JSONObject(responseBody)
            val token = d.optString("access_token", "")
            if (token.isNotEmpty()) {
                directToken = token
                directTokenExpiry = now + (d.optInt("expires_in", 7200) * 1000L)
                token
            } else {
                throw Exception("获取 Token 失败: $responseBody")
            }
        }
    }

    // ===== 发送群消息 =====
    suspend fun sendToGroup(group: String, body: Map<String, Any?>): Map<String, Any?> {
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
            val responseBody = response.body?.string() ?: "{}"
            Log.d(TAG, "sendToGroup response: $responseBody")
            val d = JSONObject(responseBody)
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception(d.getString("message"))
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    // ===== 发送私聊消息 =====
    suspend fun sendToUser(openid: String, body: Map<String, Any?>): Map<String, Any?> {
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
            val responseBody = response.body?.string() ?: "{}"
            val d = JSONObject(responseBody)
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception(d.getString("message"))
            }
            d.keys().asSequence().associateWith { d.get(it) }
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
                val body = response.body?.string() ?: "{}"
                val d = JSONObject(body)
                d.keys().asSequence().associateWith { d.get(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取群信息失败: ${e.message}")
            null
        }
    }

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
