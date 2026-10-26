package org.daycompass.android.pairing

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.daycompass.android.api.DeviceApiClient
import org.daycompass.android.api.TestValues
import org.daycompass.android.token.DeviceTokenStore
import org.daycompass.android.token.SoftwareTokenCipher
import org.daycompass.android.token.TokenCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.security.KeyStoreException

class DeviceSetupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val trusting =
        HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().let {
            OkHttpClient.Builder().sslSocketFactory(it.sslSocketFactory(), it.trustManager).build()
        }
    private val tokenFile by lazy { File(folder.root, "device_token.bin") }
    private val serverFile by lazy { File(folder.root, "server_url.txt") }

    init {
        server.useHttps(
            HandshakeCertificates
                .Builder()
                .heldCertificate(certificate)
                .build()
                .sslSocketFactory(),
        )
        server.start(InetAddress.getByName("localhost"), 0)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun setup(
        cipher: TokenCipher = SoftwareTokenCipher(),
        client: OkHttpClient = trusting,
    ) = DeviceSetup(DeviceTokenStore(tokenFile, cipher), serverFile) { DeviceApiClient(it, client) }

    @Test
    fun freshInstallIsNotPaired() {
        assertEquals(SetupState.NotPaired, setup().load())
    }

    @Test
    fun successfulPairingStoresServerAndToken() {
        val secret = TestValues.tokenValue()
        server.enqueue(pairResponse(secret))
        val setup = setup()

        assertNull(setup.pair(server.url("/"), TestValues.pairingCode(), "Pixel 8"))

        val paired = setup.load() as SetupState.Paired
        assertEquals(server.url("/"), paired.serverUrl)
        assertEquals("Bearer $secret", paired.token.authorizationHeaderValue())
    }

    @Test
    fun rejectedCodeStoresNothing() {
        server.enqueue(error(401, "invalid_pairing_code"))
        val setup = setup()

        assertEquals(PairingError.InvalidCode, setup.pair(server.url("/"), TestValues.pairingCode(), "Pixel 8"))

        assertEquals(SetupState.NotPaired, setup.load())
        assertFalse(serverFile.exists())
    }

    @Test
    fun deviceLimitIsReported() {
        server.enqueue(error(409, "device_limit_reached"))

        assertEquals(
            PairingError.DeviceLimitReached,
            setup().pair(server.url("/"), TestValues.pairingCode(), "Pixel 8"),
        )
    }

    @Test
    fun rateLimitCarriesTheDelay() {
        server.enqueue(error(429, "rate_limited").newBuilder().addHeader("Retry-After", "90").build())

        assertEquals(PairingError.RateLimited(90), setup().pair(server.url("/"), TestValues.pairingCode(), "Pixel 8"))
    }

    @Test
    fun untrustedCertificateIsToldApartFromUnreachable() {
        val error = setup(client = OkHttpClient()).pair(server.url("/"), TestValues.pairingCode(), "Pixel 8")

        assertEquals(PairingError.UntrustedCertificate, error)
    }

    @Test
    fun closedServerIsUnreachable() {
        val url = server.url("/")
        server.close()

        assertEquals(PairingError.Unreachable, setup().pair(url, TestValues.pairingCode(), "Pixel 8"))
    }

    @Test
    fun failingKeystoreReportsStorageFailure() {
        server.enqueue(pairResponse(TestValues.tokenValue()))
        val setup = setup(cipher = BrokenCipher())

        assertEquals(PairingError.StorageFailed, setup.pair(server.url("/"), TestValues.pairingCode(), "Pixel 8"))
        assertEquals(SetupState.NotPaired, setup.load())
    }

    @Test
    fun failedRepairingNeverPairsTheOldTokenWithTheNewServer() {
        val cipher = SoftwareTokenCipher()
        server.enqueue(pairResponse(TestValues.tokenValue()))
        setup(cipher).pair(server.url("/"), TestValues.pairingCode(), "Pixel 8")
        serverFile.delete()
        server.enqueue(pairResponse(TestValues.tokenValue()))
        val repairing = setup(EncryptionFailsCipher(cipher))

        assertEquals(
            PairingError.StorageFailed,
            repairing.pair(server.url("/other/"), TestValues.pairingCode(), "Pixel 8"),
        )

        assertEquals(SetupState.NotPaired, repairing.load())
    }

    @Test
    fun tokenWithoutAKnownServerNeedsRepairing() {
        server.enqueue(pairResponse(TestValues.tokenValue()))
        val setup = setup()
        setup.pair(server.url("/"), TestValues.pairingCode(), "Pixel 8")

        serverFile.delete()

        assertEquals(SetupState.NeedsRepairing, setup.load())
    }

    @Test
    fun unreadableTokenNeedsRepairing() {
        server.enqueue(pairResponse(TestValues.tokenValue()))
        setup().pair(server.url("/"), TestValues.pairingCode(), "Pixel 8")

        // A fresh cipher has a different key, as after a Keystore reset.
        assertEquals(SetupState.NeedsRepairing, setup(cipher = SoftwareTokenCipher()).load())
    }

    private fun pairResponse(token: String) =
        json(
            201,
            """{"device_token":"$token","device":{"id":"0199aaaa-0000-7000-8000-000000000001","name":"Pixel 8",
               "platform":"android","created_at":1800000000,"last_seen_at":1800000000,"revoked_at":null},
               "server_time":1800000000}""",
        )

    private fun error(
        code: Int,
        errorCode: String,
    ) = json(code, """{"error":{"code":"$errorCode","message":"irrelevant"}}""")

    private fun json(
        code: Int,
        body: String,
    ) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

private class BrokenCipher : TokenCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = throw KeyStoreException("keystore unavailable")

    override fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = throw KeyStoreException("keystore unavailable")
}

/** Can still read what [delegate] wrote, but can no longer store anything new. */
private class EncryptionFailsCipher(
    private val delegate: TokenCipher,
) : TokenCipher {
    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = throw KeyStoreException("keystore unavailable")

    override fun decrypt(
        ivAndCiphertext: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = delegate.decrypt(ivAndCiphertext, associatedData)
}
