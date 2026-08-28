package com.oxoax.bot.services

import org.json.JSONObject

object GlobalWs {
    private var service: QQWsService? = null
    private val eventListeners = mutableListOf<(JSONObject) -> Unit>()
    private val connListeners = mutableListOf<(Boolean) -> Unit>()

    val isConnected: Boolean get() = service?.isConnected ?: false

    fun init() {
        if (service != null) return
        val bid = LocalStore.botId ?: ""
        val secret = LocalStore.botSecret ?: ""
        if (bid.isEmpty() || secret.isEmpty()) return

        service = QQWsService(appId = bid, appSecret = secret).apply {
            onEvent = { event -> eventListeners.forEach { it(event) } }
            onConnectionChange = { connected -> connListeners.forEach { it(connected) } }
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

    fun ensureConnected() {
        if (service == null) {
            init()
        } else if (!service!!.isConnected) {
            service?.connect()
        }
    }

    fun reconnect() {
        service?.connect()
    }

    fun dispose() {
        service?.dispose()
        service = null
        eventListeners.clear()
        connListeners.clear()
    }
}
