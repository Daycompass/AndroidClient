package org.daycompass.android.token

import org.daycompass.android.api.DeviceToken
import org.daycompass.android.api.TestValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DeviceTokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = SoftwareTokenCipher()
    private val file by lazy { File(folder.root, "device_token.bin") }
    private val store by lazy { DeviceTokenStore(file, cipher) }

    @Test
    fun nothingStoredMeansNotPaired() {
        assertEquals(StoredToken.None, store.load())
    }

    @Test
    fun savedTokenLoadsBack() {
        val secret = TestValues.tokenValue()

        store.save(DeviceToken(secret))

        val loaded = store.load() as StoredToken.Present
        assertEquals("Bearer $secret", loaded.token.authorizationHeaderValue())
    }

    @Test
    fun fileHoldsVersionIvAndCiphertextButNoPlaintext() {
        val secret = TestValues.tokenValue()

        store.save(DeviceToken(secret))

        val stored = file.readBytes()
        assertEquals(1.toByte(), stored[0])
        // Version byte, 12-byte IV, ciphertext of the same length, 16-byte tag.
        assertEquals(1 + 12 + secret.length + 16, stored.size)
        assertFalse(String(stored, Charsets.ISO_8859_1).contains(secret.removePrefix("dcd_")))
    }

    @Test
    fun saveReplacesTheOldToken() {
        store.save(DeviceToken(TestValues.tokenValue()))
        val second = TestValues.tokenValue()

        store.save(DeviceToken(second))

        assertEquals("Bearer $second", (store.load() as StoredToken.Present).token.authorizationHeaderValue())
    }

    @Test
    fun clearForgetsTheToken() {
        store.save(DeviceToken(TestValues.tokenValue()))

        store.clear()

        assertEquals(StoredToken.None, store.load())
    }

    @Test
    fun tamperedCiphertextIsUnreadable() {
        store.save(DeviceToken(TestValues.tokenValue()))
        val stored = file.readBytes()
        stored[stored.size - 1] = (stored[stored.size - 1].toInt() xor 1).toByte()
        file.writeBytes(stored)

        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun unknownFormatVersionIsUnreadable() {
        store.save(DeviceToken(TestValues.tokenValue()))
        val stored = file.readBytes()
        stored[0] = 2
        file.writeBytes(stored)

        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun truncatedOrEmptyFileIsUnreadable() {
        file.writeBytes(byteArrayOf(1, 0, 0))
        assertEquals(StoredToken.Unreadable, store.load())

        file.writeBytes(ByteArray(0))
        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun missingKeyIsUnreadable() {
        store.save(DeviceToken(TestValues.tokenValue()))

        cipher.forgetKey()

        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun ciphertextBoundToTheContextIsRejectedElsewhere() {
        val foreign = cipher.encrypt(TestValues.tokenValue().encodeToByteArray(), "other".encodeToByteArray())
        file.writeBytes(byteArrayOf(1) + foreign)

        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun decryptedValueThatIsNoTokenIsUnreadable() {
        val associatedData = "org.daycompass.android/device-token".encodeToByteArray() + 1.toByte()
        file.writeBytes(byteArrayOf(1) + cipher.encrypt(TestValues.hex().encodeToByteArray(), associatedData))

        assertEquals(StoredToken.Unreadable, store.load())
    }

    @Test
    fun failedEncryptionStoresNothing() {
        val failing = DeviceTokenStore(file, FailingCipher())

        assertThrows(GeneralSecurityException::class.java) { failing.save(DeviceToken(TestValues.tokenValue())) }
        assertFalse(file.exists())
    }

    @Test
    fun storedTokenNeverPrintsThePlaintext() {
        val secret = TestValues.tokenValue()
        store.save(DeviceToken(secret))

        assertTrue(store.load().toString().contains("DeviceToken(***)"))
        assertFalse(store.load().toString().contains(secret))
    }
}

private class FailingCipher : TokenCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = throw KeyStoreException("keystore unavailable")

    override fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = throw KeyStoreException("keystore unavailable")
}
