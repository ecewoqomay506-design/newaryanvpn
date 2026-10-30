package com.aryan.vpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, ERROR }

/** Process-wide state shared by service, controllers and UI. */
object VpnState {
    val state = MutableStateFlow(ConnState.DISCONNECTED)
    val lastError = MutableStateFlow<String?>(null)
    val engineResult = MutableStateFlow<String?>(null)
    val connectedSince = MutableStateFlow<Long?>(null)
    val reconnectAttempts = MutableStateFlow(0)
    /** Set when the user presses Disconnect; blocks auto-reconnect. */
    @Volatile var userStopped = false
}

object ErrorExplainer {
    fun explain(e: Throwable): String {
        val m = (e.message ?: e.javaClass.simpleName).lowercase()
        return when {
            "x509" in m || "certificate" in m -> "TLS verification failed: server certificate doesn't match SNI."
            "websocket" in m || "bad handshake" in m -> "WebSocket handshake failed: check Host header and path."
            "network is unreachable" in m || "no route" in m -> "IPv6 endpoint unreachable: your network may not support IPv6."
            "timeout" in m || "timed out" in m -> "Connection timed out: endpoint unreachable or blocked."
            "decode config" in m || "json" in m || "unknown field" in m -> "Invalid engine configuration."
            "dns" in m -> "DNS failure: try a different DNS preset."
            else -> "Engine error: " + (e.message ?: "unknown").take(160)
        }.replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "<uuid>")
    }
}
