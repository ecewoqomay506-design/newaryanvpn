package com.aryan.vpn

import android.app.Application
import com.aryan.vpn.core.LatencyTester
import com.aryan.vpn.data.*
import com.aryan.vpn.vpn.*
import io.nekohasekai.libbox.Libbox

class AryanApp : Application() {
    lateinit var profiles: ProfileRepository; lateinit var dns: DnsSettingsRepository
    lateinit var settings: SettingsRepository; lateinit var latency: LatencyTester
    lateinit var vpn: VpnController; lateinit var reconnect: ReconnectionController
    val speed = SpeedMonitor()
    override fun onCreate() {
        super.onCreate()
        val dir = filesDir.absolutePath
        Libbox.setup(dir, dir, cacheDir.absolutePath, false)
        profiles = ProfileRepository(this); dns = DnsSettingsRepository(this); settings = SettingsRepository(this)
        latency = LatencyTester(this); vpn = VpnController(this); reconnect = ReconnectionController(this, settings, vpn)
    }
}
