package com.oxoax.bot.services

import android.util.Log
import org.json.JSONObject

/**
 * 全局 WebSocket 单例 - APP 生命周期内保持连接
 */
object GlobalWs {
    private const val TAG = "GlobalWs"
    private var service: QQWsService? = null
    private val eventListeners = mutableListOf<(JSONObject) -> Unit>()
    private val connListeners = mutableListOf<(Boolean) -> Unit>()
    private val logListeners = mutableListOf<(String) -> Unit>()

    val isConnected: Boolean get() = service?.isConnected ?: false

    fun init() {
        if (service != null) return
        val bid = LocalStore.botId ?: ""
        val secret = LocalStore.botSecret ?: ""
        if (bid.isEmpty() || secret.isEmpty()) {
            Log.w(TAG, "未配置 botId/botSecret，跳过初始化")
            return
        }

        Log.d(TAG, "初始化 WebSocket, appId=$bid")
        service = QQWsService(appId = bid, appSecret = secret).apply {
            onEvent = { event ->
                eventListeners.forEach { it(event) }
            }
            onConnectionChange = { connected ->
                Log.d(TAG, "连接状态: $connected")
                connListeners.forEach { it(connected) }
            }
            onLog = { msg ->
                Log.d(TAG, msg)
                logListeners.forEach { it(msg) }
            }
        }
        service?.connect()
    }

    fun addEventListener(listener: (JSONObject) -> Unit) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: (JSONObject) -> Unit) {
        eventListeners.remove(listener)
    }

    fun addConnectionListener(listener: (Boolean) -> Unit) {
        connListeners.add(listener)
    }

    fun removeConnectionListener(listener: (Boolean) -> Unit) {
        connListeners.remove(listener)
    }

    fun addLogListener(listener: (String) -> Unit) {
        logListeners.add(listener)
    }

    fun removeLogListener(listener: (String) -> Unit) {
        logListeners.remove(listener)
    }

    fun ensureConnected() {
        if (service == null) {
            init()
        } else if (!service!!.isConnected) {
            service?.connect()
        }
    }

    fun reconnect() {
        service?.disconnect()
        service = null
        init()
    }

    fun getService(): QQWsService? = service

    fun dispose() {
        service?.dispose()
        service = null
        eventListeners.clear()
        connListeners.clear()
        logListeners.clear()
    }
}
