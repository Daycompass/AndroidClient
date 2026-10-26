package org.daycompass.android.token

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a non-exportable AndroidKeyStore key. The Keystore picks a random
 * 12-byte IV for every encryption.
 *
 * The key needs neither user authentication nor an unlocked device, because the background
 * sync has to read the token. StrongBox is tried first; without it the Keystore falls back
 * to the TEE or, on devices without one, to software.
 */
class KeystoreTokenCipher(
    private val alias: String = DEFAULT_ALIAS,
) : TokenCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        if (iv.size != IV_BYTES) throw GeneralSecurityException("unexpected IV length")
        return iv + ciphertext
    }

    override fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        if (ivAndCiphertext.size <= IV_BYTES) throw GeneralSecurityException("truncated ciphertext")
        val key = existingKey() ?: throw KeyStoreException("token key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ivAndCiphertext, 0, IV_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ivAndCiphertext, IV_BYTES, ivAndCiphertext.size - IV_BYTES)
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun createKey(): SecretKey =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createStrongBoxKey() ?: generateKey(specBuilder())
        } else {
            generateKey(specBuilder())
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createStrongBoxKey(): SecretKey? =
        try {
            generateKey(specBuilder().setIsStrongBoxBacked(true))
        } catch (_: StrongBoxUnavailableException) {
            null
        } catch (_: ProviderException) {
            // Some devices report a broken StrongBox this way instead.
            null
        }

    private fun specBuilder() =
        KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)

    private fun generateKey(spec: KeyGenParameterSpec.Builder): SecretKey =
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec.build()) }
            .generateKey()

    private companion object {
        const val DEFAULT_ALIAS = "daycompass_device_token"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
