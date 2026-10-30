package com.aryan.vpn.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.vpn.AryanApp
import com.aryan.vpn.R
import com.aryan.vpn.core.*
import com.aryan.vpn.vpn.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Bg = Color(0xFF05070F); private val Card = Color(0xFF0B1226); private val Stroke = Color(0xFF1C3A7A)
private val Cyan = Color(0xFF00C8FF); private val Blue = Color(0xFF2F6BFF); private val Purple = Color(0xFF7B5CFF)
private val Muted = Color(0xFF8A97B8); private val Ok = Color(0xFF2EE59D); private val Bad = Color(0xFFFF5C7A)

enum class Tab(val label: Int, val icon: ImageVector) { HOME(R.string.nav_home, Icons.Default.Home), PROFILES(R.string.nav_profiles, Icons.Default.Dns),
    LATENCY(R.string.nav_latency, Icons.Default.Speed), DNS(R.string.nav_dns, Icons.Default.Storage), SETTINGS(R.string.nav_settings, Icons.Default.Settings) }

@Composable
fun AryanRoot(app: AryanApp, onConnect: () -> Unit, currentLang: String, onToggleLang: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var diag by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.speed.start(scope) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Bg, surface = Card)) {
        Scaffold(containerColor = Bg,
            topBar = { TopBar(currentLang, onToggleLang) },
            bottomBar = { NavigationBar(containerColor = Color(0xFF070B18)) {
                Tab.entries.forEach { t -> NavigationBarItem(selected = tab == t && !diag, onClick = { tab = t; diag = false },
                    icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label), fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0x2200C8FF), unselectedIconColor = Muted, unselectedTextColor = Muted)) } } }
        ) { pad -> Box(Modifier.padding(pad).fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF0A1030))))) {
            if (diag) DiagnosticsScreen(app) else when (tab) {
                Tab.HOME -> HomeScreen(app, onConnect, { tab = it }, { diag = true })
                Tab.PROFILES -> ProfilesScreen(app, onConnect)
                Tab.LATENCY -> LatencyScreen(app)
                Tab.DNS -> DnsScreen(app)
                Tab.SETTINGS -> SettingsScreen(app, currentLang, onToggleLang)
            } } }
    }
}

/** Top-corner language pill: shows the language you'd switch TO (FA ⇄ EN). */
@Composable
private fun TopBar(lang: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("▲", color = Cyan, fontSize = 26.sp); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(stringResource(R.string.tagline), color = Muted, fontSize = 11.sp)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, Cyan, RoundedCornerShape(50))
            .background(Color(0x2200C8FF)).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (lang == "fa") "EN" else "FA", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable private fun NeonCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Card).border(1.dp, Stroke, RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp), content = content)

@Composable private fun stateLabel(s: ConnState) = stringResource(when (s) {
    ConnState.DISCONNECTED -> R.string.st_disconnected; ConnState.CONNECTING -> R.string.st_connecting; ConnState.CONNECTED -> R.string.st_connected
    ConnState.RECONNECTING -> R.string.st_reconnecting; ConnState.DISCONNECTING -> R.string.st_disconnecting; ConnState.ERROR -> R.string.st_error })

@Composable
fun HomeScreen(app: AryanApp, onConnect: () -> Unit, go: (Tab) -> Unit, openDiag: () -> Unit) {
    val state by VpnState.state.collectAsStateWithLifecycle()
    val since by VpnState.connectedSince.collectAsStateWithLifecycle()
    val err by VpnState.lastError.collectAsStateWithLifecycle()
    val down by app.speed.down.collectAsStateWithLifecycle(); val up by app.speed.up.collectAsStateWithLifecycle()
    val tunnel by app.latency.lastTunnel.collectAsStateWithLifecycle()
    val auto by app.settings.autoReconnect.collectAsStateWithLifecycle()
    app.profiles.selectedId.collectAsStateWithLifecycle().value
    val profile = app.profiles.selected()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val active = state == ConnState.CONNECTED
    val busy = state in setOf(ConnState.CONNECTING, ConnState.RECONNECTING, ConnState.DISCONNECTING)
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(200.dp).drawBehind {
            drawCircle(Brush.radialGradient(listOf((if (active) Ok else Cyan).copy(alpha = if (busy) pulse * .5f else .35f), Color.Transparent)), size.minDimension / 1.6f)
        }.clip(CircleShape).border(4.dp, Brush.sweepGradient(listOf(Cyan, Blue, Purple, Cyan)), CircleShape).background(Color(0xFF081028))
            .clickable(enabled = !busy) { if (active) { app.reconnect.cancel(); app.vpn.stop() } else { app.reconnect.cancel(); onConnect() } },
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(56.dp))
                Text(stringResource(if (active) R.string.disconnect else R.string.connect), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("● " + stateLabel(state), color = when (state) { ConnState.CONNECTED -> Ok; ConnState.ERROR -> Bad; else -> Muted })
        err?.takeIf { state == ConnState.ERROR }?.let { Text(it, color = Bad, fontSize = 12.sp) }
        Spacer(Modifier.height(14.dp))
        NeonCard(Modifier.fillMaxWidth(), onClick = { go(Tab.PROFILES) }) {
            Text(profile?.name ?: stringResource(R.string.no_profiles), color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("VLESS • TLS • WebSocket", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
            Stat(Icons.Default.Download, R.string.download, if (active) SpeedMonitor.fmt(down) else "0.0 KB/s", Cyan)
            Stat(Icons.Default.Upload, R.string.upload, if (active) SpeedMonitor.fmt(up) else "0.0 KB/s", Purple)
            Stat(Icons.Default.NetworkCheck, R.string.latency, if (active) tunnel?.ms?.let { "$it ms" } ?: "-- ms" else "-- ms", Ok)
            Stat(Icons.Default.Timer, R.string.duration, since?.let { fmtDur(now - it) } ?: "00:00:00", Cyan)
        } }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.auto_reconnect), color = Color.White); Text(stringResource(R.string.auto_reconnect_sub), color = Muted, fontSize = 11.sp) }
            Switch(auto, { app.settings.setAutoReconnect(it); if (!it) app.reconnect.cancel() })
        } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            NeonCard(Modifier.weight(1f), onClick = { go(Tab.DNS) }) { Text(stringResource(R.string.dns_settings), color = Color.White); Text(stringResource(R.string.dns_sub), color = Muted, fontSize = 11.sp) }
            NeonCard(Modifier.weight(1f), onClick = { go(Tab.PROFILES) }) { Text(stringResource(R.string.server_profiles), color = Color.White); Text(stringResource(R.string.profiles_sub), color = Muted, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(12.dp))
        NeonCard(Modifier.fillMaxWidth(), onClick = openDiag) { Text(stringResource(R.string.diagnostics), color = Color.White); Text(stringResource(R.string.diag_sub), color = Muted, fontSize = 11.sp) }
    }
}

@Composable private fun Stat(icon: ImageVector, label: Int, value: String, tint: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(icon, null, tint = tint); Text(stringResource(label), color = Muted, fontSize = 11.sp); Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}
private fun fmtDur(ms: Long): String { val s = ms / 1000; return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }

@Composable
fun ProfilesScreen(app: AryanApp, onConnect: () -> Unit) {
    val list by app.profiles.profiles.collectAsStateWithLifecycle()
    val sel by app.profiles.selectedId.collectAsStateWithLifecycle()
    val res by app.latency.results.collectAsStateWithLifecycle()
    var sorted by remember { mutableStateOf(false) }
    var uri by remember { mutableStateOf("") }; var uriErr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current
    val shown = if (sorted) list.sortedBy { res[it.id]?.ms ?: Long.MAX_VALUE } else list
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ scope.launch { app.latency.testAll(list) } }) { Text(stringResource(R.string.test_all)) }
            OutlinedButton({ sorted = !sorted }) { Text(stringResource(R.string.sort_latency)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(uri, { uri = it; uriErr = false }, Modifier.weight(1f), singleLine = true, isError = uriErr,
                label = { Text(stringResource(if (uriErr) R.string.invalid_uri else R.string.import_uri)) })
            TextButton({ app.profiles.import(uri, null).onSuccess { uri = "" }.onFailure { uriErr = true } }) { Text(stringResource(R.string.add)) }
        }
        if (list.isEmpty()) Text(stringResource(R.string.no_profiles), color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown, key = { it.id }) { p ->
            val r = res[p.id]
            NeonCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = if (p.id == sel) Cyan else Color.White, fontWeight = FontWeight.SemiBold)
                        Text(p.displayAddress, color = Muted, fontSize = 11.sp)
                        Text(statusText(r), color = statusColor(r), fontSize = 12.sp)
                        r?.takeIf { it.status != TestStatus.TESTING }?.let { Text(stringResource(R.string.last_test, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.at))) + if (it.isStale) " (${stringResource(R.string.stale)})" else "", color = Muted, fontSize = 10.sp) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton({ scope.launch { app.latency.testEndpoint(p) } }) { Text(stringResource(R.string.test)) }
                        TextButton({ app.profiles.select(p.id) }) { Text(stringResource(if (p.id == sel) R.string.selected else R.string.select)) }
                        Button({ app.profiles.select(p.id); if (VpnState.state.value == ConnState.CONNECTED) app.vpn.restartIfActive() else onConnect() }) { Text(stringResource(R.string.connect)) }
                    }
                }
                Row { TextButton({ clip.setText(AnnotatedString(p.rawUri)) }) { Text(stringResource(R.string.export), fontSize = 11.sp) }
                    TextButton({ app.profiles.delete(p.id) }) { Text(stringResource(R.string.delete), color = Bad, fontSize = 11.sp) } }
            }
        } }
    }
}

@Composable private fun statusText(r: LatencyResult?): String = when (r?.status) {
    null, TestStatus.UNKNOWN -> stringResource(R.string.unknown)
    TestStatus.TESTING -> stringResource(R.string.testing)
    TestStatus.AVAILABLE -> "${stringResource(R.string.tcp_tls)}: ${r.ms} ms"
    TestStatus.TIMEOUT -> stringResource(R.string.timeout)
    TestStatus.UNAVAILABLE -> stringResource(R.string.unavailable) + (r.error?.let { " · $it" } ?: "")
}
private fun statusColor(r: LatencyResult?) = when (r?.status) { TestStatus.AVAILABLE -> Ok; TestStatus.TIMEOUT, TestStatus.UNAVAILABLE -> Bad; else -> Muted }

@Composable
fun LatencyScreen(app: AryanApp) {
    val res by app.latency.results.collectAsStateWithLifecycle()
    val proxy by app.latency.lastProxy.collectAsStateWithLifecycle(); val tunnel by app.latency.lastTunnel.collectAsStateWithLifecycle()
    val state by VpnState.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope(); val p = app.profiles.selected()
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(p?.name ?: "", color = Color.White, fontWeight = FontWeight.Bold)
        Row3(R.string.tcp_tls, p?.let { res[it.id] })
        Row3(R.string.proxy_latency, proxy)
        Row3(R.string.tunnel_latency, tunnel)
        if (state != ConnState.CONNECTED) Text(stringResource(R.string.tunnel_needs_vpn), color = Muted, fontSize = 12.sp)
        Button(enabled = p != null && !busy, onClick = { scope.launch { busy = true; p?.let {
            app.latency.testEndpoint(it)
            if (state == ConnState.CONNECTED) app.latency.tunnelProbe() else app.latency.testProxy(it, app.dns.servers())
        }; busy = false } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.testing else R.string.test)) }
    }
}
@Composable private fun Row3(label: Int, r: LatencyResult?) = NeonCard(Modifier.fillMaxWidth()) {
    Row { Text(stringResource(label), color = Color.White, modifier = Modifier.weight(1f))
        Text(when (r?.status) { TestStatus.AVAILABLE -> "${r.ms} ms"; TestStatus.TIMEOUT -> stringResource(R.string.timeout)
            TestStatus.UNAVAILABLE -> stringResource(R.string.unavailable); TestStatus.TESTING -> stringResource(R.string.testing); else -> "--" }, color = statusColor(r)) }
}

@Composable
fun DnsScreen(app: AryanApp) {
    val cur by app.dns.preset.collectAsStateWithLifecycle()
    var pick by remember(cur) { mutableStateOf(cur) }
    var custom by remember { mutableStateOf(app.dns.custom.value) }
    var msg by remember { mutableStateOf<String?>(null) }
    val invalid = stringResource(R.string.invalid_dns, "%s"); val ok = stringResource(R.string.dns_applied)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DnsPreset.entries.forEach { d -> NeonCard(Modifier.fillMaxWidth(), onClick = { pick = d }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(pick == d, { pick = d })
                Column { Text(if (d == DnsPreset.CUSTOM) stringResource(R.string.custom_dns) else d.label, color = Color.White)
                    if (d.servers.isNotEmpty()) Text(d.servers.joinToString(" / "), color = Muted, fontSize = 11.sp) }
            } } }
        if (pick == DnsPreset.CUSTOM) OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.custom_dns_hint)) })
        Text(stringResource(R.string.dns_note), color = Muted, fontSize = 11.sp)
        msg?.let { Text(it, color = if (it == ok) Ok else Bad, fontSize = 12.sp) }
        Button({ val bad = app.dns.apply(pick, custom); msg = bad?.let { invalid.format(it) } ?: ok.also { app.vpn.restartIfActive() } }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_dns)) }
        OutlinedButton({ app.dns.restoreDefault(); custom = ""; msg = ok; app.vpn.restartIfActive() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_default)) }
    }
}

@Composable
fun DiagnosticsScreen(app: AryanApp) {
    val state by VpnState.state.collectAsStateWithLifecycle(); val eng by VpnState.engineResult.collectAsStateWithLifecycle()
    val err by VpnState.lastError.collectAsStateWithLifecycle(); val att by VpnState.reconnectAttempts.collectAsStateWithLifecycle()
    val net by app.reconnect.networkType.collectAsStateWithLifecycle(); val dns by app.dns.preset.collectAsStateWithLifecycle()
    val res by app.latency.results.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    val p = app.profiles.selected()
    val none = stringResource(R.string.none)
    var cfgCheck by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NeonCard(Modifier.fillMaxWidth()) {
            KV(R.string.conn_state, stateLabel(state)); KV(R.string.sel_profile, p?.name ?: none)
            KV(R.string.engine, eng ?: none); KV(R.string.dns_settings, if (dns == DnsPreset.CUSTOM) "Custom" else dns.label)
            KV(R.string.network, net); KV(R.string.reconnect_attempts, att.toString())
            KV(R.string.tcp_tls, p?.let { res[it.id]?.ms?.let { "$it ms" } } ?: "--"); KV(R.string.last_error, err ?: none)
            cfgCheck?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Button({ scope.launch {
            cfgCheck = p?.let { pr -> runCatching { io.nekohasekai.libbox.Libbox.checkConfig(ConfigBuilder.build(pr, app.dns.servers())); "Config: valid" }
                .getOrElse { "Config: " + ErrorExplainer.explain(it) } } ?: "No profile"
            p?.let { app.latency.testEndpoint(it) }
        } }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.run_diag)) }
    }
}
@Composable private fun KV(k: Int, v: String) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    Text(stringResource(k), color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp); Text(v, color = Color.White, fontSize = 13.sp)
}

@Composable
fun SettingsScreen(app: AryanApp, lang: String, onToggleLang: () -> Unit) {
    val auto by app.settings.autoReconnect.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonCard(Modifier.fillMaxWidth(), onClick = onToggleLang) { Row { Text(stringResource(R.string.language), color = Color.White, modifier = Modifier.weight(1f)); Text(if (lang == "fa") "فارسی" else "English", color = Cyan) } }
        NeonCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.auto_reconnect), color = Color.White, modifier = Modifier.weight(1f)); Switch(auto, { app.settings.setAutoReconnect(it) }) } }
        NeonCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.about), color = Color.White); Text(stringResource(R.string.version), color = Muted, fontSize = 12.sp) }
    }
}
