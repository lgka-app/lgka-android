package com.lgka

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import lgka.api.Login
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The school's shared read-only credentials, typed by the user at login and
 * verified by api.lgka.app. Stored AES-GCM-encrypted with a key that never
 * leaves the Android Keystore; the preferences file is excluded from backups
 * (res/xml/backup_rules.xml). Nothing in the source tree contains a password,
 * and the WebView never sees the credentials — only the API client does.
 */
class Credentials(context: Context) {
    private val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "lgka-auth-v2"
        private const val LEGACY_PREFS = "lgka-auth"
        private const val KEY_ALIAS = "lgka-credentials"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
    }

    // load() runs on the main thread (root navigation, every sync, WebView auth); each
    // Keystore decrypt is a binder call, so the pair is decrypted once per process.
    private var cached: Login? = null
    private var cacheValid = false

    init {
        migrateLegacy()
    }

    @Synchronized
    fun load(): Login? {
        if (!cacheValid) {
            cached = decryptStored()
            cacheValid = true
        }
        return cached
    }

    @Synchronized
    fun save(login: Login) {
        sp.edit {
            putString(KEY_USER, encrypt(login.user))
            putString(KEY_PASSWORD, encrypt(login.password))
        }
        cached = login
        cacheValid = true
    }

    @Synchronized
    fun clear() {
        sp.edit { clear() }
        cached = null
        cacheValid = true
    }

    private fun decryptStored(): Login? {
        val user = decrypt(sp.getString(KEY_USER, null)) ?: return null
        val password = decrypt(sp.getString(KEY_PASSWORD, null)) ?: return null
        return Login(user, password)
    }

    /** Installs before the API switch kept the pair in plain private prefs; move it once. */
    private fun migrateLegacy() {
        val user = legacy.getString(KEY_USER, null)
        val password = legacy.getString(KEY_PASSWORD, null)
        if (user != null && password != null && load() == null) save(Login(user, password))
        if (legacy.all.isNotEmpty()) legacy.edit { clear() }
    }

    // ── Keystore-backed AES/GCM ──────────────────────────────────────────────

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String? {
        if (stored == null) return null
        return try {
            val (iv, data) = stored.split(":", limit = 2).let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            null // key rotated/invalidated: behave as signed out
        }
    }
}
