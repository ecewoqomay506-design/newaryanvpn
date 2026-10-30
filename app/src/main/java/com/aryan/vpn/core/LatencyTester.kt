package com.aryan.vpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aryan.vpn.vpn.PlatformBridgeNoTun
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.*
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class MeasureType { ENDPOINT_TCP_TLS, PROXY, TUNNEL }
enum class TestStatus { UNKNOWN, TESTING, AVAILABLE, UNAVAILABLE, TIMEOUT }
data class LatencyResult(val type: MeasureType, val status: TestStatus, val ms: Long? = null, val at: Long = System.currentTimeMillis(), val error: String? = null) {
    val isStale get() = System.currentTimeMillis() - at > 5 * 60_000
}

class LatencyTester(private val ctx: Context) {
    val results = MutableStateFlow<Map<String, LatencyResult>>(emptyMap())   // profileId -> endpoint result
    val lastProxy = MutableStateFlow<LatencyResult?>(null)
    val lastTunnel = MutableStateFlow<LatencyResult?>(null)
    private val running = mutableSetOf<String>()
    private val proxyMutex = Mutex()
    private val TIMEOUT = 5000

    /** TCP connect + TLS handshake to the endpoint. NOT VPN latency. Bypasses the tunnel. */
    suspend fun testEndpoint(p: VlessProfile): LatencyResult? = withContext(Dispatchers.IO) {
        synchronized(running) { if (!running.add(p.id)) return@withContext null }   // no duplicate tests
        results.value = results.value + (p.id to LatencyResult(MeasureType.ENDPOINT_TCP_TLS, TestStatus.TESTING))
        val r = try {
            val start = System.nanoTime()
            val network = underlyingNetwork() ?: throw NoRouteToHostException("No non-VPN internet network")
            // Resolve on the same underlying network the socket is bound to, then try each address.
            // This is important on IPv4-only networks where the profile's original address is IPv6.
            val addresses = network.getAllByName(p.connectionHost)
            var lastFailure: Exception? = null
            var result: LatencyResult? = null
            for (address in addresses) {
                val raw = Socket()
                try {
                    network.bindSocket(raw)
                    raw.connect(InetSocketAddress(address, p.port), TIMEOUT)
                    raw.soTimeout = TIMEOUT
                    val tlsPeer = p.sni?.takeIf { it.isNotBlank() && !it.contains(':') } ?: p.connectionHost
                    val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(raw, tlsPeer, p.port, true) as SSLSocket
                    ssl.sslParameters = ssl.sslParameters.apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                        p.sni?.takeIf { it.isNotBlank() && !it.contains(':') }?.let { serverNames = listOf(SNIHostName(it)) }
                    }
                    ssl.startHandshake() // verify certificate chain and peer hostname
                    val ms = (System.nanoTime() - start) / 1_000_000
                    ssl.close()
                    result = LatencyResult(MeasureType.ENDPOINT_TCP_TLS, TestStatus.AVAILABLE, ms)
                    break
                } catch (e: Exception) {
                    lastFailure = e
                    runCatching { raw.close() }
                }
            }
            result ?: throw (lastFailure ?: NoRouteToHostException("No address available"))
        } catch (e: SocketTimeoutException) { LatencyResult(MeasureType.ENDPOINT_TCP_TLS, TestStatus.TIMEOUT, error = "Timeout")
        } catch (e: Exception) {
            val msg = if (e is NoRouteToHostException || e.message?.contains("ENETUNREACH") == true)
                "No route to server (IPv6 or DNS path unavailable)" else e.javaClass.simpleName
            LatencyResult(MeasureType.ENDPOINT_TCP_TLS, TestStatus.UNAVAILABLE, error = msg)
        } finally { synchronized(running) { running.remove(p.id) } }
        results.value = results.value + (p.id to r); r
    }

    suspend fun testAll(list: List<VlessProfile>) = coroutineScope { list.map { async { testEndpoint(it) } }.awaitAll() }

    /** Real HTTP request through a temporary local sing-box proxy (no tunnel). */
    suspend fun testProxy(p: VlessProfile, dns: List<String>): LatencyResult = proxyMutex.withLock {
        withContext(Dispatchers.IO) {
            val port = ServerSocket(0).use { it.localPort }
            val svc = try { Libbox.newService(ConfigBuilder.build(p, dns, mixedPort = port), PlatformBridgeNoTun()).also { it.start() } }
                catch (e: Exception) { return@withContext LatencyResult(MeasureType.PROXY, TestStatus.UNAVAILABLE, error = "Engine start failed").also { lastProxy.value = it } }
            try { httpProbe(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)), MeasureType.PROXY) }
            finally { runCatching { svc.close() } }
        }.also { lastProxy.value = it }
    }

    /** Request routed through the active VPN (default network = tun). */
    suspend fun tunnelProbe(): Long? = withContext(Dispatchers.IO) {
        httpProbe(Proxy.NO_PROXY, MeasureType.TUNNEL).also { lastTunnel.value = it }.ms
    }

    private fun httpProbe(proxy: Proxy, type: MeasureType): LatencyResult = try {
        val c = URL("http://cp.cloudflare.com/generate_204").openConnection(proxy) as HttpURLConnection
        c.connectTimeout = TIMEOUT; c.readTimeout = TIMEOUT; c.useCaches = false
        val s = System.nanoTime(); val code = c.responseCode; val ms = (System.nanoTime() - s) / 1_000_000; c.disconnect()
        if (code == 204 || code == 200) LatencyResult(type, TestStatus.AVAILABLE, ms) else LatencyResult(type, TestStatus.UNAVAILABLE, error = "HTTP $code")
    } catch (e: SocketTimeoutException) { LatencyResult(type, TestStatus.TIMEOUT, error = "Timeout")
    } catch (e: Exception) { LatencyResult(type, TestStatus.UNAVAILABLE, error = e.javaClass.simpleName) }

    private fun underlyingNetwork(): android.net.Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val active = cm.activeNetwork
        if (active != null && cm.getNetworkCapabilities(active)?.let {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true) return active
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { n -> cm.getNetworkCapabilities(n)?.let {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true }
    }
}
