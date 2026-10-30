package com.aryan.vpn.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnService
import android.os.Process
import com.aryan.vpn.AryanApp
import com.aryan.vpn.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class VpnController(private val ctx: Context) {
    fun start() {
        val app = ctx.applicationContext as AryanApp
        if (app.profiles.selected() == null) {
            VpnState.lastError.value = "Import a VLESS profile before connecting"
            VpnState.state.value = ConnState.ERROR
            return
        }
        if (VpnService.prepare(ctx) != null) { VpnState.lastError.value = "VPN permission not granted"; VpnState.state.value = ConnState.ERROR; return }
        VpnState.userStopped = false
        ctx.startForegroundService(Intent(ctx, AryanVpnService::class.java))
    }
    fun stop() {
        VpnState.userStopped = true
        ctx.startService(Intent(ctx, AryanVpnService::class.java).setAction(AryanVpnService.ACTION_STOP))
    }
    fun restartIfActive() { if (VpnState.state.value == ConnState.CONNECTED) { stop(); CoroutineScope(Dispatchers.Main).launch { delay(800); start() } } }
}

/** Exponential backoff (2s..60s), single in-flight attempt, cancelled by manual disconnect. */
class ReconnectionController(private val ctx: Context, private val settings: SettingsRepository, private val vpn: VpnController) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    val networkType = MutableStateFlow("Unknown")

    init {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                networkType.value = when { c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"; else -> "Other" }
                if (VpnState.state.value == ConnState.ERROR) onFailure()
            }
            override fun onLost(n: Network) { networkType.value = "None" }
        })
    }

    fun onFailure() {
        if (!settings.autoReconnect.value || VpnState.userStopped || job?.isActive == true) return
        job = scope.launch {
            val attempt = ++VpnState.reconnectAttempts.value
            delay(minOf(60_000L, 2_000L shl (attempt - 1).coerceAtMost(5)))
            if (VpnState.userStopped || VpnService.prepare(ctx) != null) return@launch
            VpnState.state.value = ConnState.RECONNECTING
            withContext(Dispatchers.Main) { vpn.start() }
        }
    }
    fun cancel() { job?.cancel(); VpnState.reconnectAttempts.value = 0 }
}

/**
 * Speed from TrafficStats for this app's UID. With the tunnel active, the engine's socket traffic is
 * attributed to our UID, so this reflects VPN traffic (including TLS/WS overhead), not device-wide traffic.
 */
class SpeedMonitor {
    val down = MutableStateFlow(0L); val up = MutableStateFlow(0L)
    private var job: Job? = null
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            val uid = Process.myUid(); var rx = TrafficStats.getUidRxBytes(uid); var tx = TrafficStats.getUidTxBytes(uid)
            while (isActive) {
                delay(1000)
                if (VpnState.state.value != ConnState.CONNECTED) { down.value = 0; up.value = 0 }
                else { val r = TrafficStats.getUidRxBytes(uid); val t = TrafficStats.getUidTxBytes(uid)
                    down.value = (r - rx).coerceAtLeast(0); up.value = (t - tx).coerceAtLeast(0) }
                rx = TrafficStats.getUidRxBytes(uid); tx = TrafficStats.getUidTxBytes(uid)
            }
        }
    }
    companion object { fun fmt(bps: Long) = if (bps >= 1024 * 1024) "%.1f MB/s".format(bps / 1048576.0) else "%.1f KB/s".format(bps / 1024.0) }
}
