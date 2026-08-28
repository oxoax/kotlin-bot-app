package com.oxoax.bot.services

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

class QQWsService(
    private val appId: String,
    private val appSecret: String
) {
    companion object {
        private const val TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken"
        private const val GATEWAY_URL = "https://api.sgroup.qq.com/gateway"
        private const val SEND_GROUP_URL = "https://api.sgroup.qq.com/v2/groups"
        private const val SEND_C2C_URL = "https://api.sgroup.qq.com/v2/users"

        private const val INTENT_GROUP_C2C = 1 shl 25
        private const val INTENT_DIRECT_MSG = 1 shl 12
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var heartbeatIntervalMs = 30000
    private var lastSeq: Int? = null
    private var sessionId: String? = null
    private var accessToken: String? = null
    private var tokenExpiry = 0L
    private var connected = false
    private var identifySent = false
    private var manualDisconnect = false

    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onEvent: ((JSONObject) -> Unit)? = null
    var onConnectionChange: ((Boolean) -> Unit)? = null

    val isConnected: Boolean get() = connected

    fun connect() {
        if (connected) return
        manualDisconnect = false
        reconnectAttempts = 0

        scope.launch {
            try {
                refreshToken()
                val gateway = fetchGateway()
                connectWs(gateway)
            } catch (e: Exception) {
                println("[QQWs] 连接失败: $e")
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        cleanup()
    }

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
            val d = JSONObject(response.body?.string() ?: "{}")
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
            val d = JSONObject(response.body?.string() ?: "{}")
            if (d.has("code") && d.optInt("code") != 0 && d.has("message")) {
                throw Exception("发送失败: ${d.getString("message")}")
            }
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    suspend fun uploadMedia(groupOpenId: String, url: String, fileType: Int, srvSendMsg: String): Map<String, Any?> {
        ensureToken()
        val body = JSONObject().apply {
            put("file_type", fileType)
            put("url", url)
            put("srv_send_msg", srvSendMsg)
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
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    suspend fun recallMessage(groupOpenId: String, messageId: String): Map<String, Any?> {
        ensureToken()
        val request = Request.Builder()
            .url("$SEND_GROUP_URL/$groupOpenId/messages/$messageId")
            .addHeader("Authorization", "QQBot $accessToken")
            .delete()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            d.keys().asSequence().associateWith { d.get(it) }
        }
    }

    fun dispose() {
        manualDisconnect = true
        cleanup()
        scope.cancel()
    }

    private suspend fun ensureToken() {
        val now = System.currentTimeMillis()
        if (accessToken == null || now >= tokenExpiry - 60000) {
            refreshToken()
        }
    }

    private suspend fun refreshToken() {
        println("[QQWs] 获取 access token...")
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
            val d = JSONObject(response.body?.string() ?: "{}")
            accessToken = d.optString("access_token")
            tokenExpiry = System.currentTimeMillis() + (d.optInt("expires_in", 7200) * 1000L)
            println("[QQWs] Token 获取成功")
        }
    }

    private suspend fun fetchGateway(): String {
        ensureToken()
        val request = Request.Builder()
            .url(GATEWAY_URL)
            .addHeader("Authorization", "QQBot $accessToken")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val d = JSONObject(response.body?.string() ?: "{}")
            d.getString("url")
        }
    }

    private suspend fun connectWs(gatewayUrl: String) {
        val request = Request.Builder().url(gatewayUrl).build()

        withContext(Dispatchers.IO) {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    println("[QQWs] WebSocket 已连接")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    println("[QQWs] WebSocket 关闭中: $code $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    println("[QQWs] WebSocket 已关闭: $code $reason")
                    onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("[QQWs] WebSocket 错误: ${t.message}")
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
                10 -> {
                    heartbeatIntervalMs = payload.getJSONObject("d").getInt("heartbeat_interval")
                    println("[QQWs] Hello 收到，心跳间隔: ${heartbeatIntervalMs}ms")
                    startHeartbeat()
                    sendIdentify()
                }
                0 -> {
                    val t = payload.optString("t", "")
                    val seq = if (payload.has("s") && !payload.isNull("s")) payload.optInt("s") else null
                    val d = payload.optJSONObject("d")
                    if (seq != null) lastSeq = seq
                    if (t == "READY") {
                        sessionId = d?.optString("session_id")
                        connected = true
                        reconnectAttempts = 0
                        onConnectionChange?.invoke(true)
                        println("[QQWs] 就绪! session_id: $sessionId")
                    }
                    onEvent?.invoke(payload)
                }
                9 -> {
                    println("[QQWs] Session 无效，重新 Identify")
                    identifySent = false
                    lastSeq = null
                    sessionId = null
                    sendIdentify()
                }
                11 -> { }
                else -> println("[QQWs] 未处理 OP $op")
            }
        } catch (e: Exception) {
            println("[QQWs] 解析消息失败: $e")
        }
    }

    private fun onDisconnected() {
        println("[QQWs] 连接断开")
        connected = false
        identifySent = false
        heartbeatJob?.cancel()
        onConnectionChange?.invoke(false)
        if (!manualDisconnect) scheduleReconnect()
    }

    private fun onError(error: Throwable) {
        println("[QQWs] 错误: ${error.message}")
        connected = false
        onConnectionChange?.invoke(false)
        if (!manualDisconnect) scheduleReconnect()
    }

    private fun sendIdentify() {
        if (identifySent) return
        identifySent = true
        val payload = JSONObject().apply {
            put("op", 2)
            put("d", JSONObject().apply {
                put("token", "QQBot $accessToken")
                put("intent", INTENT_GROUP_C2C or INTENT_DIRECT_MSG)
                put("shard", org.json.JSONArray(listOf(0, 1)))
                put("properties", JSONObject().apply {
                    put("os", "android")
                    put("browser", "kotlin")
                    put("device", "phone")
                })
            })
        }
        println("[QQWs] 发送 Identify (intents: ${INTENT_GROUP_C2C or INTENT_DIRECT_MSG})")
        send(payload)
    }

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

    private fun scheduleReconnect() {
        if (manualDisconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            println("[QQWs] 放弃重连（已达最大次数）")
            return
        }
        reconnectAttempts++
        val delayMs = min(1000L * (1 shl (reconnectAttempts - 1)), 30000L)
        println("[QQWs] $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS 次重连，等待 ${delayMs / 1000}s")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            cleanup()
            try {
                refreshToken()
                val gateway = fetchGateway()
                connectWs(gateway)
            } catch (e: Exception) {
                println("[QQWs] 重连失败: $e")
                scheduleReconnect()
            }
        }
    }

    private fun send(data: JSONObject) {
        webSocket?.send(data.toString())
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        identifySent = false
        connected = false
        try { webSocket?.close(1000, "bye") } catch (_: Exception) {}
        webSocket = null
    }

    data class ParsedEvent(
        val type: String,
        val event: String,
        val data: JSONObject,
        val groupOpenid: String?,
        val authorId: String?,
        val authorName: String?,
        val content: String?,
        val timestamp: String?,
        val id: String?
    )

    fun parseEvent(raw: JSONObject): ParsedEvent? {
        val t = raw.optString("t", "") ?: return null
        val d = raw.optJSONObject("d") ?: return null

        return when (t) {
            "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE", "C2C_MESSAGE_CREATE" -> {
                val author = d.optJSONObject("author")
                ParsedEvent(
                    type = if (t.startsWith("GROUP")) "group" else "c2c",
                    event = t,
                    data = d,
                    groupOpenid = d.optString("group_openid", null),
                    authorId = author?.optString("member_openid") ?: author?.optString("user_openid"),
                    authorName = author?.optString("member_name") ?: author?.optString("username"),
                    content = d.optString("content", null),
                    timestamp = d.optString("timestamp", null),
                    id = d.optString("id", null)
                )
            }
            else -> null
        }
    }
}
