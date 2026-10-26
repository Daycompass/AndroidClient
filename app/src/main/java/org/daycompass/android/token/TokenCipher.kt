package org.daycompass.android.token

import java.security.GeneralSecurityException

/** Authenticated encryption for the device token. */
interface TokenCipher {
    /** Returns IV ‖ ciphertext, the GCM tag included. */
    @Throws(GeneralSecurityException::class)
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray

    /** Throws if the key is gone or [ivAndCiphertext] fails authentication. */
    @Throws(GeneralSecurityException::class)
    fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}
