package org.daycompass.android.token

import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM in software with a key generated per test, standing in for the Keystore. */
class SoftwareTokenCipher : TokenCipher {
    private var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val random = SecureRandom()

    fun forgetKey() {
        key = null
    }

    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        if (ivAndCiphertext.size <= 12) throw GeneralSecurityException("truncated ciphertext")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key ?: throw KeyStoreException("token key missing"),
            GCMParameterSpec(128, ivAndCiphertext, 0, 12),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ivAndCiphertext, 12, ivAndCiphertext.size - 12)
    }
}
