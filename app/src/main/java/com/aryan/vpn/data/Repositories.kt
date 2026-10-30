package com.aryan.vpn.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aryan.vpn.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Profiles are encrypted at rest with an app-local Android Keystore AES-GCM key. */
class ProfileRepository(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<VlessProfile>> = _profiles
    private val _selected = MutableStateFlow(prefs.getString("selected", null) ?: _profiles.value.firstOrNull()?.id)
    val selectedId: StateFlow<String?> = _selected

    private fun load(): List<VlessProfile> {
        val encrypted = prefs.getString(ENCRYPTED_LIST, null)
        if (encrypted != null) {
            val clear = runCatching { ProfileSecretStorage.decrypt(encrypted) }.getOrNull() ?: return emptyList()
            val decoded = decodeProfiles(clear) ?: return emptyList()
            if (prefs.contains(LEGACY_LIST)) {
                check(prefs.edit().remove(LEGACY_LIST).commit()) { "Could not remove plaintext profile storage" }
            }
            return decoded
        }

        // Migrate the old plaintext format if an earlier local build created it. Never keep the
        // plaintext copy if the secure migration cannot be completed.
        val legacy = prefs.getString(LEGACY_LIST, null) ?: return DefaultProfiles.load()
        val migrated = runCatching {
            val decoded = decodeProfiles(legacy) ?: return@runCatching null
            storeEncrypted(legacy)
            decoded
        }.getOrNull()
        if (migrated == null) {
            prefs.edit().remove(LEGACY_LIST).commit()
            return emptyList()
        }
        check(prefs.edit().remove(LEGACY_LIST).commit()) { "Could not remove plaintext profile storage" }
        return migrated
    }

    private fun decodeProfiles(raw: String): List<VlessProfile>? = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { VlessParser.parse(it.getString("uri"), it.getString("name")).copy(id = it.getString("id")) }
    }.getOrNull()

    private fun save(list: List<VlessProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.rawUri)) }
        storeEncrypted(arr.toString())
        _profiles.value = list
    }

    private fun storeEncrypted(clear: String) {
        val encrypted = ProfileSecretStorage.encrypt(clear)
        check(prefs.edit().putString(ENCRYPTED_LIST, encrypted).remove(LEGACY_LIST).commit()) {
            "Could not securely save profiles"
        }
    }

    fun select(id: String) { prefs.edit().putString("selected", id).apply(); _selected.value = id }
    fun selected() = _profiles.value.firstOrNull { it.id == _selected.value } ?: _profiles.value.firstOrNull()
    fun import(uri: String, name: String?): Result<VlessProfile> = runCatching {
        VlessParser.parse(uri, name?.takeIf { it.isNotBlank() }).also { save(_profiles.value + it) }
    }
    fun update(id: String, name: String, uri: String) = runCatching {
        val p = VlessParser.parse(uri, name).copy(id = id); save(_profiles.value.map { if (it.id == id) p else it })
    }
    fun delete(id: String) { save(_profiles.value.filterNot { it.id == id }); if (_selected.value == id) _selected.value = _profiles.value.firstOrNull()?.id }
    fun restoreDefaults() = save(DefaultProfiles.load())

    private companion object {
        const val ENCRYPTED_LIST = "profiles_aes_gcm_v1"
        const val LEGACY_LIST = "list"
    }
}

/** Keystore-backed encryption for profile URIs, including UUID credentials. */
private object ProfileSecretStorage {
    private const val KEY_ALIAS = "aryan-vpn-profile-key-v1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(clear: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clear.toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "Encrypted profile data is truncated" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, packed.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size)), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }
}

class DnsSettingsRepository(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("dns", Context.MODE_PRIVATE)
    val preset = MutableStateFlow(runCatching { DnsPreset.valueOf(prefs.getString("preset", "CLOUDFLARE")!!) }.getOrDefault(DnsPreset.CLOUDFLARE))
    val custom = MutableStateFlow(prefs.getString("custom", "") ?: "")
    fun servers(): List<String> = if (preset.value == DnsPreset.CUSTOM)
        custom.value.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() } else preset.value.servers
    /** Returns null on success or the first invalid entry. */
    fun apply(p: DnsPreset, customText: String): String? {
        if (p == DnsPreset.CUSTOM) {
            val list = customText.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) return "(empty)"
            list.firstOrNull { !DnsValidator.isValid(it) }?.let { return it }
        }
        prefs.edit().putString("preset", p.name).putString("custom", customText).apply()
        preset.value = p; custom.value = customText; return null
    }
    fun restoreDefault() = apply(DnsPreset.CLOUDFLARE, "")
}

class SettingsRepository(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val autoReconnect = MutableStateFlow(prefs.getBoolean("auto_reconnect", true))
    fun setAutoReconnect(v: Boolean) { prefs.edit().putBoolean("auto_reconnect", v).apply(); autoReconnect.value = v }
}
