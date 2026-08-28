package com.oxoax.bot.services

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * QQ Bot 直连 WebSocket 服务
 * 直接连接 QQ 官方网关，收发消息不走中间服务器
 *
 * 协议流程:
 * 1. POST https://bots.qq.com/app/getAppAccessToken 获取 access_token
 * 2. GET  https://api.sgroup.qq.com/gateway 获取 WebSocket 网关地址
 * 3. 连接 WebSocket，收到 Hello (op=10)
 * 4. 发送 Identify (op=2)，携带 token + intents
 * 5. 收到 READY (op=0, t=READY) 表示连接成功
 * 6. 定时发送心跳 (op=1)
 */
class QQWsService(
    private val appId: String,
    private val appSecret: String
) {
    companion object {
        private const val TAG = "QQWs"
        private const val TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken"
        private const val GATEWAY_URL = "https://api.sgroup.qq.com/gateway"
        private const val SEND_GROUP_URL = "https://api.sgroup.qq.com/v2/groups"
        private const val SEND_C2C_URL = "https://api.sgroup.qq.com/v2/users"

        // Intents
        private const val INTENT_GROUP_AT_MESSAGE = 1 shl 25  // 群@消息
        private const val INTENT_C2C_MESSAGE = 1 shl 25       // 私聊消息 (同一个 intent)
        private const val INTENT_DIRECT_MSG = 1 shl 12        // 频道私信

        private const val MAX_RECONNECT = 10
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // WebSocket
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var heartbeatIntervalMs = 41250  // 默认值，收到 Hello 后更新
    private var lastSeq: Int? = null
    private var sessionId: String? = null

    // Token
    private var accessToken: String? = null
    private var tokenExpiry = 0L

    // 状态
    private var connected = false
    private var identifySent = false
    private var manualDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    // 回调
    var onEvent: ((JSONObject) -> Unit)? = null
    var onConnectionChange: ((Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    val isConnected: Boolean get() = connected

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke("[QQWs] $msg")
    }

    // ==================== 公开方法 ====================

    fun connect() {
        if (connected) return
        manualDisconnect = false
        reconnectAttempts = 0
        scope.launch {
            try {
                log("开始连接...")
                refreshToken()
                val gateway = fetchGateway()
                log("网关地址: $gateway")
                connectWs(gateway)
            } catch (e: Exception) {
                log("连接失败: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        cleanup()
    }

    fun dispose() {
        manualDisconnect = true
        cleanup()
        scope.cancel()
    }

    // ==================== Token 管理 ====================

    private suspend fun refreshToken() {
        log("获取 access token...")
        val body = JSONObject().apply {
            put("appId", appId)
            put("clientSecret", appSecret)
        }
        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val d = JSONObject(responseBody)
            accessToken = d.optString("access_token", "")
            val expiresIn = d.optInt("expires_in", 7200)
            tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000L)
            if (accessToken.isNullOrEmpty()) {
                throw Exception("获取 Token 失败: $responseBody")
            }
            log("Token 获取成功, expires_in=${expiresIn}s")
        }
    }

    private suspend fun ensureToken() {
        val now = System.currentTimeMillis()
        if (accessToken.isNullOrEmpty() || now >= tokenExpiry - 60000) {
            refreshToken()
        }
    }

    // ==================== 网关 ====================

    private suspend fun fetchGateway(): String {
        ensureToken()
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .addHeader("Authorization", "QQBot $accessToken")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val d = JSONObject(body)
            val url = d.optString("url", "")
            if (url.isEmpty()) throw Exception("获取网关失败: $body")
            url
        }
    }

    // ==================== WebSocket ====================

    private suspend fun connectWs(gatewayUrl: String) {
        val request = Request.Builder().url(gatewayUrl).build()

        withContext(Dispatchers.IO) {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    log("WebSocket 已连接")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    log("收到消息: ${text.take(200)}")
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    log("WebSocket 关闭中: $code $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    log("WebSocket 已关闭: $code $reason")
                    onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    log("WebSocket 错误: ${t.message}")
                    onError(t)
                }
            })
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val payload = JSONObject(raw)
            val op = payload.getInt("op")

            when (op) {
                10 -> { // Hello - 服务端打招呼，告知心跳间隔
                    val d = payload.optJSONObject("d")
                    heartbeatIntervalMs = d?.optInt("heartbeat_interval", 41250) ?: 41250
                    log("Hello 收到，心跳间隔: ${heartbeatIntervalMs}ms")
                    startHeartbeat()
                    sendIdentify()
                }
                0 -> { // Dispatch - 事件分发
                    val t = payload.optString("t", "")
                    val seq = if (payload.has("s") && !payload.isNull("s")) payload.optInt("s") else null
                    val d = payload.optJSONObject("d")
                    if (seq != null) lastSeq = seq

                    if (t == "READY") {
                        sessionId = d?.optString("session_id")
                        connected = true
                        reconnectAttempts = 0
                        onConnectionChange?.invoke(true)
                        log("就绪! session_id: $sessionId")
                    }

                    // 把完整事件抛出去
                    onEvent?.invoke(payload)
                }
                9 -> { // Invalid Session
                    log("Session 无效，重新 Identify")
                    identifySent = false
                    lastSeq = null
                    sessionId = null
                    // 延迟 1-5 秒后重连
                    scope.launch {
                        delay((1000..5000).random().toLong())
                        sendIdentify()
                    }
                }
                11 -> { // Heartbeat ACK
                    // log("心跳确认")
                }
                else -> log("未处理 OP $op")
            }
        } catch (e: Exception) {
            log("解析消息失败: ${e.message}")
        }
    }

    private fun onDisconnected() {
        log("连接断开")
        connected = false
        identifySent = false
        heartbeatJob?.cancel()
        onConnectionChange?.invoke(false)
        if (!manualDisconnect) scheduleReconnect()
    }

    private fun onError(error: Throwable) {
        log("错误: ${error.message}")
        connected = false
        identifySent = false
        heartbeatJob?.cancel()
        onConnectionChange?.invoke(false)
        if (!manualDisconnect) scheduleReconnect()
    }

    // ==================== 认证 ====================

    private fun sendIdentify() {
        if (identifySent) return
        identifySent = true
        val payload = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", "QQBot $accessToken")
                put("intents", INTENT_GROUP_AT_MESSAGE or INTENT_C2C_MESSAGE)
                put("shard", JSONArray().apply {
                    put(0)
                    put(1)
                })
                put("properties", JSONObject().apply {
                    put("os", "android")
                    put("browser", "kotlin")
                    put("device", "phone")
                })
            })
        }
        log("发送 Identify (intents: ${INTENT_GROUP_AT_MESSAGE or INTENT_C2C_MESSAGE})")
        send(payload)
    }

    // ==================== 心跳 ====================

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs.toLong())
                sendHeartbeat()
            }
        }
    }

    private fun sendHeartbeat() {
        val payload = JSONObject().apply {
            put("op", 1)
            putOpt("d", lastSeq)
        }
        send(payload)
    }

    // ==================== 重连 ====================

    private fun scheduleReconnect() {
        if (manualDisconnect || reconnectAttempts >= MAX_RECONNECT) {
            log("放弃重连")
            return
        }
        reconnectAttempts++
        val delayMs = min(1000L * (1 shl (reconnectAttempts - 1)), 30000L)
        log("$reconnectAttempts/$MAX_RECONNECT 次重连，等待 ${delayMs / 1000}s")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            cleanup()
            try {
                refreshToken()
                val gateway = fetchGateway()
                connectWs(gateway)
            } catch (e: Exception) {
                log("重连失败: ${e.message}")
                scheduleReconnect()
            }
        }
    }

    // ==================== 工具 ====================

    private fun send(data: JSONObject) {
        try {
            webSocket?.send(data.toString())
        } catch (e: Exception) {
            log("发送失败: ${e.message}")
        }
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        identifySent = false
        connected = false
        try { webSocket?.close(1000, "bye") } catch (_: Exception) {}
        webSocket = null
    }

    // ==================== 发送消息 ====================

    suspend fun sendGroupMessage(groupOpenId: String, body: Map<String, Any?>): Map<String, Any?> {
        ensureToken()
        val jsonBody = JSONObject(body).toString()
        val request = Request.Builder()
            .url("$SEND_GROUP_URL/$groupOpenId/messages")
            .addHeader("Authorization", "QQBot $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val d = JSONObject(responseBody)
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception("发送失败: ${d.getString("message")}")
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    suspend fun sendC2CMessage(openId: String, body: Map<String, Any?>): Map<String, Any?> {
        ensureToken()
        val jsonBody = JSONObject(body).toString()
        val request = Request.Builder()
            .url("$SEND_C2C_URL/$openId/messages")
            .addHeader("Authorization", "QQBot $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val d = JSONObject(responseBody)
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception("发送失败: ${d.getString("message")}")
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    suspend fun recallMessage(groupOpenId: String, messageId: String): Boolean {
        ensureToken()
        val request = Request.Builder()
            .url("$SEND_GROUP_URL/$groupOpenId/messages/$messageId")
            .addHeader("Authorization", "QQBot $accessToken")
            .delete()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.code == 200 || response.code == 204
        }
    }

    suspend fun uploadMedia(groupOpenId: String, url: String, fileType: Int): String? {
        ensureToken()
        val body = JSONObject().apply {
            put("file_type", fileType)
            put("url", url)
            put("srv_send_msg", false)
        }
        val request = Request.Builder()
            .url("$SEND_GROUP_URL/$groupOpenId/files")
            .addHeader("Authorization", "QQBot $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            d.optString("file_info", null)
        }
    }

    suspend fun fetchGroupInfo(groupOpenid: String): Map<String, Any?>? {
        return try {
            ensureToken()
            val request = Request.Builder()
                .url("https://api.sgroup.qq.com/v2/groups/$groupOpenid/info")
                .addHeader("Authorization", "QQBot $accessToken")
                .get()
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val d = JSONObject(body)
                d.keys().asSequence().associateWith { d.get(it) }
            }
        } catch (e: Exception) {
            log("获取群信息失败: ${e.message}")
            null
        }
    }
}
