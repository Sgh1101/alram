package com.alram.mail.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Gmail 앱 비밀번호를 Android Keystore 키로 암호화해서 보관한다. */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secret", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun save(value: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREF, Base64.encodeToString(cipher.iv + enc, Base64.NO_WRAP)).apply()
    }

    fun load(): String? = runCatching {
        val raw = Base64.decode(prefs.getString(PREF, null) ?: return null, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORM)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    fun has(): Boolean = prefs.contains(PREF)

    fun clear() {
        prefs.edit().remove(PREF).apply()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "alram_secret"
        const val PREF = "gmail_app_password"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
