package org.daycompass.android.token

import android.content.Context
import androidx.core.util.AtomicFile
import org.daycompass.android.api.DeviceToken
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

sealed interface StoredToken {
    /** Never paired. */
    data object None : StoredToken

    data class Present(
        val token: DeviceToken,
    ) : StoredToken

    /**
     * A token file exists but cannot be decrypted, for example after a restore or a
     * Keystore reset. Handle it like a `401`: keep the buffer, ask the user to pair again,
     * do not retry.
     */
    data object Unreadable : StoredToken
}

/**
 * Keeps the device token encrypted in `noBackupFilesDir`.
 *
 * File format: one format version byte, then IV ‖ ciphertext. The associated data binds the
 * ciphertext to [CONTEXT] and the format version.
 */
class DeviceTokenStore(
    file: File,
    private val cipher: TokenCipher,
) {
    private val atomicFile = AtomicFile(file)

    @Synchronized
    fun load(): StoredToken =
        try {
            decode(atomicFile.readFully())
        } catch (_: FileNotFoundException) {
            StoredToken.None
        } catch (_: IOException) {
            StoredToken.Unreadable
        }

    /**
     * Replaces any stored token. If this throws, nothing new was stored and the device has
     * to be paired again with a fresh code.
     */
    @Synchronized
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(token: DeviceToken) {
        val plaintext = token.plaintextForEncryptedStorage()
        val encrypted =
            try {
                cipher.encrypt(plaintext, ASSOCIATED_DATA)
            } finally {
                plaintext.fill(0)
            }
        val out = atomicFile.startWrite()
        try {
            out.write(byteArrayOf(FORMAT_VERSION))
            out.write(encrypted)
            atomicFile.finishWrite(out)
        } catch (e: IOException) {
            atomicFile.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun clear() {
        atomicFile.delete()
    }

    private fun decode(stored: ByteArray): StoredToken {
        val plaintext = decryptOrNull(stored) ?: return StoredToken.Unreadable
        return try {
            StoredToken.Present(DeviceToken(plaintext.decodeToString()))
        } catch (_: IllegalArgumentException) {
            StoredToken.Unreadable
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decryptOrNull(stored: ByteArray): ByteArray? {
        if (stored.firstOrNull() != FORMAT_VERSION) return null
        return try {
            cipher.decrypt(stored.copyOfRange(1, stored.size), ASSOCIATED_DATA)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        }
    }

    companion object {
        private const val FILE_NAME = "device_token.bin"
        private const val FORMAT_VERSION: Byte = 1
        private const val CONTEXT = "org.daycompass.android/device-token"
        private val ASSOCIATED_DATA = CONTEXT.encodeToByteArray() + FORMAT_VERSION

        fun create(context: Context) =
            DeviceTokenStore(File(context.noBackupFilesDir, FILE_NAME), KeystoreTokenCipher())
    }
}
