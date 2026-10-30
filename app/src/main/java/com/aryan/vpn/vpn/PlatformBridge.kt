package com.aryan.vpn.vpn

import io.nekohasekai.libbox.*

/**
 * PlatformInterface for sing-box libbox 1.9.7 (pinned).
 * 
 * Strategy:
 * - usePlatformDefaultInterfaceMonitor = true  → prevents netlink ban
 * - start/close monitor are empty               → prevents Go stack split crash
 * This is the safest combination for v1.9.7 on Android.
 */
class PlatformBridge(
    private val svc: AryanVpnService
) : PlatformInterface {

    override fun usePlatformAutoDetectInterfaceControl() = true

    override fun autoDetectInterfaceControl(fd: Int) {
        svc.protect(fd)
    }

    override fun openTun(options: TunOptions): Int =
        svc.openTun(options)

    override fun useProcFS() = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ) = -1

    override fun packageNameByUid(uid: Int) = ""

    override fun uidByPackageName(packageName: String) = -1

    // Critical: tell libbox we handle the monitor (so it won't use netlink)
    override fun usePlatformDefaultInterfaceMonitor() = true

    // But do NOT actually register any Android callback (avoids stack split crash)
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        // intentionally empty for stability on libbox 1.9.7
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        // intentionally empty
    }

    override fun usePlatformInterfaceGetter() = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        error("Platform interface getter is disabled")

    override fun underNetworkExtension() = false

    override fun includeAllNetworks() = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun writeLog(message: String) {
        // Avoid logging potentially sensitive connection details.
    }
}

/**
 * PlatformInterface for temporary proxy-latency checks.
 */
class PlatformBridgeNoTun : PlatformInterface {

    override fun usePlatformAutoDetectInterfaceControl() = false

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int =
        error("TUN is unavailable in probe mode")

    override fun useProcFS() = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ) = -1

    override fun packageNameByUid(uid: Int) = ""

    override fun uidByPackageName(packageName: String) = -1

    override fun usePlatformDefaultInterfaceMonitor() = false

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun usePlatformInterfaceGetter() = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        error("Platform interface getter is disabled")

    override fun underNetworkExtension() = false

    override fun includeAllNetworks() = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun writeLog(message: String) {}
}
