package com.aryan.vpn.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

enum class DnsPreset(val label: String, val servers: List<String>) {
    CLOUDFLARE("Cloudflare", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")),
    GOOGLE("Google Public DNS", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")),
    QUAD9("Quad9", listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")),
    ADGUARD("AdGuard DNS", listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff")),
    CUSTOM("Custom DNS", emptyList()),
}

object DnsValidator {
    private val v4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    /** Literal-only validation: never triggers a DNS lookup. */
    fun isValid(addr: String): Boolean {
        val a = addr.trim()
        if (v4.matches(a)) return true
        if (!a.contains(':') || !a.all { it.isLetterOrDigit() || it == ':' || it == '.' }) return false
        return runCatching { InetAddress.getByName(a) is Inet6Address }.getOrDefault(false)
    }
    fun isV4(a: String) = v4.matches(a.trim())
}
