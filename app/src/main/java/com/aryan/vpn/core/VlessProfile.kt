package com.aryan.vpn.core

import java.net.URI
import java.net.URLDecoder
import java.util.UUID

data class VlessProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uuid: String,
    val host: String,          // server address (IPv6 without brackets)
    val port: Int,
    val encryption: String = "none",
    val network: String = "ws",
    val security: String = "tls",
    val sni: String? = null,
    val wsHost: String? = null,
    val wsPath: String = "/",
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val rawUri: String,
) {
    val isIpv6 get() = host.contains(':')
    /**
     * IPv6-only links often also carry a TLS SNI/WebSocket host name. Use that DNS name for
     * outbound resolution so IPv4-only access networks can use its A record when available.
     * Keep the original endpoint as a last resort when no usable hostname is supplied.
     */
    val connectionHost: String
        get() = if (!isIpv6) host else sequenceOf(sni, wsHost)
            .filterNotNull()
            .firstOrNull { it.isNotBlank() && !it.contains(':') && !it.contains('/') && !it.any(Char::isWhitespace) }
            ?: host
    /** Safe for UI: never shows UUID. */
    val displayAddress get() = if (host.length > 18) host.take(16) + "…" else host
    override fun toString() = "VlessProfile(name=$name, host=<redacted>, port=$port)" // keep creds out of logs
}

object VlessParser {
    class ParseException(msg: String) : Exception(msg)

    fun parse(uri: String, nameOverride: String? = null): VlessProfile {
        val trimmed = uri.trim().removeSurrounding("\"")
        if (!trimmed.startsWith("vless://", ignoreCase = true)) throw ParseException("Not a vless:// URI")
        val u = try { URI(trimmed) } catch (e: Exception) { throw ParseException("Malformed URI") }
        val uuid = u.rawUserInfo?.let { dec(it) } ?: throw ParseException("Missing UUID")
        runCatching { UUID.fromString(uuid) }.getOrElse { throw ParseException("Invalid UUID") }
        val host = u.host?.removePrefix("[")?.removeSuffix("]") ?: throw ParseException("Missing host")
        val port = u.port.takeIf { it in 1..65535 } ?: throw ParseException("Invalid port")
        val q = (u.rawQuery ?: "").split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to dec(it.substringAfter('=')) }
        val network = q["type"] ?: "tcp"
        if (network != "ws") throw ParseException("Only WebSocket transport is supported")
        val security = q["security"] ?: "none"
        if (security != "tls") throw ParseException("Only TLS security is supported")
        return VlessProfile(
            name = nameOverride ?: u.rawFragment?.let { dec(it) } ?: "VLESS",
            uuid = uuid, host = host, port = port,
            encryption = q["encryption"] ?: "none", network = network, security = security,
            sni = q["sni"], wsHost = q["host"], wsPath = q["path"] ?: "/",
            fingerprint = q["fp"], alpn = q["alpn"]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            rawUri = trimmed,
        )
    }
    private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
}
