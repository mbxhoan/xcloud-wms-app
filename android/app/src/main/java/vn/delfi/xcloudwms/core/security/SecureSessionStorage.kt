package vn.delfi.xcloudwms.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val tokenType: String,
    val userId: String?,
    val email: String?,
)

class SecureSessionStorage(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(): StoredAuthSession? {
        val encrypted = sharedPreferences.getString(KEY_SESSION_PAYLOAD, null) ?: return null
        val decrypted = runCatching { decrypt(encrypted) }.getOrNull() ?: return null
        return runCatching {
            val payload = JSONObject(decrypted)
            StoredAuthSession(
                accessToken = payload.optString("access_token").orEmpty(),
                refreshToken = payload.optString("refresh_token").orEmpty(),
                expiresAtEpochSeconds = payload.optLong("expires_at", 0L),
                tokenType = payload.optString("token_type", "bearer"),
                userId = payload.optString("user_id").takeIf { it.isNotBlank() },
                email = payload.optString("email").takeIf { it.isNotBlank() },
            )
        }.getOrNull()?.takeIf {
            it.accessToken.isNotBlank() && it.refreshToken.isNotBlank()
        }
    }

    fun saveSession(session: StoredAuthSession) {
        val payload = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtEpochSeconds)
            .put("token_type", session.tokenType)
            .put("user_id", session.userId)
            .put("email", session.email)
            .toString()

        sharedPreferences.edit()
            .putString(KEY_SESSION_PAYLOAD, encrypt(payload))
            .apply()
    }

    fun clearSession() {
        sharedPreferences.edit()
            .remove(KEY_SESSION_PAYLOAD)
            .apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val combined = ByteBuffer.allocate(Int.SIZE_BYTES + iv.size + encryptedBytes.size)
            .putInt(iv.size)
            .put(iv)
            .put(encryptedBytes)
            .array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encodedPayload: String): String {
        val combined = Base64.decode(encodedPayload, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(combined)
        val ivSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        return cipher.doFinal(encryptedBytes).toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "xcloud_wms_secure_session"
        const val KEY_SESSION_PAYLOAD = "session_payload"
        const val KEY_ALIAS = "xcloud_wms_auth_session_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
