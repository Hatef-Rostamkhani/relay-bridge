package io.github.hatefrostamkhani.relaybridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("relaybridge_config", Context.MODE_PRIVATE)

    fun load(): AppConfig {
        val scriptId = prefs.getString(KEY_SCRIPT_ID, "").orEmpty()
        val encryptedAuthKey = prefs.getString(KEY_AUTH_KEY, null)
        val authKey = encryptedAuthKey?.let { decrypt(it) }.orEmpty()
        val mode = RelayMode.fromStored(prefs.getString(KEY_MODE, null))
        return AppConfig(scriptId = scriptId, authKey = authKey, mode = mode)
    }

    fun save(config: AppConfig) {
        prefs.edit()
            .putString(KEY_SCRIPT_ID, config.scriptId.trim())
            .putString(KEY_AUTH_KEY, encrypt(config.authKey))
            .putString(KEY_MODE, config.mode.name)
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        return try {
            val packed = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
            val ivSize = packed.int
            val iv = ByteArray(ivSize)
            packed.get(iv)
            val ciphertext = ByteArray(packed.remaining())
            packed.get(ciphertext)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "RelayBridgeAuthKey"
        private const val KEY_AUTH_KEY = "auth_key"
        private const val KEY_MODE = "mode"
        private const val KEY_SCRIPT_ID = "script_id"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
