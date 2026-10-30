package com.aryan.vpn.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.aryan.vpn.AryanApp
import com.aryan.vpn.MainActivity
import com.aryan.vpn.R
import com.aryan.vpn.core.ConfigBuilder
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.*

/**
 * Fixed for libbox 1.9.7 on Android (especially MIUI):
 * - BoxService.start() runs on a dedicated HandlerThread
 *   to avoid "runtime: stack split at bad time" during OpenTun callback.
 */
class AryanVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var box: BoxService? = null
    private var tunFd: ParcelFileDescriptor? = null
    private val lock = Any()

    // Dedicated thread for starting the Go engine (larger effective stack)
    private val starterThread = HandlerThread("libbox-starter").apply { start() }
    private val starterHandler = Handler(starterThread.looper)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    stopEngine()
                    stopSelf()
                }
            }
            else -> {
                startForeground(1, notification())
                // Start engine on dedicated thread (not Main, not IO pool)
                starterHandler.post {
                    startEngine()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startEngine() {
        synchronized(lock) {
            if (box != null) return

            val app = application as AryanApp
            val profile = app.profiles.selected()
            if (profile == null) {
                fail(IllegalStateException("No profile selected"))
                return
            }

            // Update UI state on Main Thread
            Handler(Looper.getMainLooper()).post {
                VpnState.state.value = if (VpnState.reconnectAttempts.value > 0) {
                    ConnState.RECONNECTING
                } else {
                    ConnState.CONNECTING
                }
            }

            try {
                val cfg = ConfigBuilder.build(profile, app.dns.servers())
                Libbox.checkConfig(cfg)

                val svc = Libbox.newService(cfg, PlatformBridge(this))
                svc.start()
                box = svc

                Handler(Looper.getMainLooper()).post {
                    VpnState.engineResult.value = "Started"
                }

                // Tunnel probe on background
                scope.launch {
                    val rtt = app.latency.tunnelProbe()
                    withContext(Dispatchers.Main) {
                        if (rtt == null) {
                            closeQuietly()
                            fail(IllegalStateException("Tunnel verification timeout"))
                        } else {
                            VpnState.state.value = ConnState.CONNECTED
                            VpnState.connectedSince.value = System.currentTimeMillis()
                            VpnState.lastError.value = null
                            VpnState.reconnectAttempts.value = 0
                        }
                    }
                }
            } catch (e: Throwable) {
                closeQuietly()
                fail(e)
            }
        }
    }

    private fun fail(e: Throwable) {
        Handler(Looper.getMainLooper()).post {
            VpnState.engineResult.value = "Failed"
            VpnState.lastError.value = ErrorExplainer.explain(e)
            VpnState.state.value = ConnState.ERROR
            VpnState.connectedSince.value = null
            (application as AryanApp).reconnect.onFailure()
        }
    }

    fun stopEngine() {
        synchronized(lock) {
            Handler(Looper.getMainLooper()).post {
                VpnState.state.value = ConnState.DISCONNECTING
            }
            closeQuietly()
            Handler(Looper.getMainLooper()).post {
                VpnState.connectedSince.value = null
                VpnState.state.value = ConnState.DISCONNECTED
            }
        }
    }

    private fun closeQuietly() {
        runCatching { box?.close() }
        box = null
        runCatching { tunFd?.close() }
        tunFd = null
    }

    /** Called by libbox when the tun inbound starts. */
    fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("VPN permission not granted")

        val b = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(options.mtu)

        val v4 = options.inet4Address
        while (v4.hasNext()) {
            val a = v4.next()
            b.addAddress(a.address(), a.prefix())
        }

        val v6 = options.inet6Address
        while (v6.hasNext()) {
            val a = v6.next()
            b.addAddress(a.address(), a.prefix())
        }

        if (options.autoRoute) {
            b.addRoute("0.0.0.0", 0)
            b.addRoute("::", 0)
            b.addDnsServer(options.dnsServerAddress)
        }

        val fd = b.establish() ?: error("VPN interface establish() returned null")
        tunFd = fd
        return fd.fd
    }

    override fun onRevoke() {
        VpnState.userStopped = true
        scope.launch {
            stopEngine()
            stopSelf()
        }
    }

    override fun onDestroy() {
        closeQuietly()
        scope.cancel()
        starterThread.quitSafely()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "vpn")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.st_connected))
            .setSmallIcon(R.drawable.ic_fg)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.aryan.vpn.STOP"
    }
}
