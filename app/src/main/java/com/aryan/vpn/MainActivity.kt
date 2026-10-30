package com.aryan.vpn

import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aryan.vpn.ui.AryanRoot
import com.aryan.vpn.vpn.ConnState
import com.aryan.vpn.vpn.VpnState

class MainActivity : AppCompatActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) (application as AryanApp).vpn.start()
        else { VpnState.lastError.value = getString(R.string.perm_denied); VpnState.state.value = ConnState.ERROR }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AryanApp
        setContent {
            AryanRoot(app,
                onConnect = { VpnService.prepare(this)?.let { vpnPermission.launch(it) } ?: app.vpn.start() },
                currentLang = currentLang(),
                onToggleLang = { setLang(if (currentLang() == "fa") "en" else "fa") })
        }
    }
    private fun currentLang() = AppCompatDelegate.getApplicationLocales()[0]?.language ?: resources.configuration.locales[0].language
    /** Persisted automatically (autoStoreLocales); layout flips to RTL for Farsi. */
    private fun setLang(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}
