package org.daycompass.android.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.daycompass.android.api.DeviceApiClient
import org.daycompass.android.api.TestValues
import org.daycompass.android.buffer.BufferedEvent
import org.daycompass.android.buffer.FakeBufferDao
import org.daycompass.android.pairing.DeviceSetup
import org.daycompass.android.pairing.SetupState
import org.daycompass.android.token.DeviceTokenStore
import org.daycompass.android.token.SoftwareTokenCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress

class EventSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val client =
        HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().let {
            OkHttpClient.Builder().sslSocketFactory(it.sslSocketFactory(), it.trustManager).build()
        }
    private val dao = FakeBufferDao()
    private val loggedRejections = mutableListOf<Map<String, Int>>()
    private val setup by lazy {
        DeviceSetup(
            DeviceTokenStore(File(folder.root, "device_token.bin"), SoftwareTokenCipher()),
            File(folder.root, "server_url.txt"),
        ) { DeviceApiClient(it, client) }
    }
    private val sync by lazy {
        EventSync(dao, setup, { DeviceApiClient(it, client) }, { loggedRejections += it }, clock = { NOW_MILLIS })
    }

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

    private fun pair() {
        server.enqueue(
            json(
                201,
                """{"device_token":"${TestValues.tokenValue()}","device":{"id":"$DEVICE_ID",
                   "name":"Pixel 8","platform":"android","created_at":1,"last_seen_at":1,"revoked_at":null},
                   "server_time":1}""",
            ),
        )
        setup.pair(server.url("/"), TestValues.pairingCode(), "Pixel 8")
        server.takeRequest()
    }

    private fun buffer(count: Int) =
        List(count) { BufferedEvent(TestValues.hex(), 1_800_000_000L + it, 60, "org.example.alpha") }
            .also(dao::insertEvents)

    @Test
    fun withoutPairingNothingIsSent() {
        buffer(3)

        assertEquals(SyncResult.NotPaired, sync.upload())
        assertEquals(0, server.requestCount)
        assertEquals(3, dao.eventCount())
    }

    @Test
    fun acceptedDuplicateAndRejectedEventsLeaveTheBuffer() {
        pair()
        buffer(3)
        server.enqueue(
            json(
                200,
                ingest(
                    """{"index":0,"status":"accepted"},{"index":1,"status":"duplicate"},
                       {"index":2,"status":"rejected","reason":"start_ts_too_old"}""",
                ),
            ),
        )

        assertEquals(SyncResult.Done, sync.upload())

        assertEquals(0, dao.eventCount())
        assertEquals(listOf(mapOf("start_ts_too_old" to 1)), loggedRejections)
        val sent = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["events"]!!.jsonArray
        assertEquals(3, sent.size)
    }

    @Test
    fun largeBuffersGoInBatchesOfFiveHundred() {
        pair()
        buffer(501)
        server.enqueue(json(200, ingest(List(500) { """{"index":$it,"status":"accepted"}""" }.joinToString(","))))
        server.enqueue(json(200, ingest("""{"index":0,"status":"accepted"}""")))

        assertEquals(SyncResult.Done, sync.upload())

        assertEquals(0, dao.eventCount())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun unauthorizedStopsKeepsTheBufferAndAsksToPairAgain() {
        pair()
        buffer(2)
        server.enqueue(json(401, """{"error":{"code":"unauthenticated","message":"x"}}"""))

        assertEquals(SyncResult.NeedsRepairing, sync.upload())

        assertEquals(2, dao.eventCount())
        assertEquals(SetupState.NeedsRepairing, setup.load())
        // No further device request until the user pairs again.
        assertEquals(SyncResult.NeedsRepairing, sync.upload())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimitKeepsTheBufferAndCarriesTheDelay() {
        pair()
        buffer(2)
        server.enqueue(
            json(
                429,
                """{"error":{"code":"rate_limited","message":"x"}}""",
            ).newBuilder().addHeader("Retry-After", "120").build(),
        )

        assertEquals(SyncResult.RateLimited(120), sync.upload())
        assertEquals(2, dao.eventCount())
    }

    @Test
    fun unreachableServerKeepsTheBuffer() {
        pair()
        buffer(2)
        server.close()

        assertEquals(SyncResult.Unreachable, sync.upload())
        assertEquals(2, dao.eventCount())
    }

    @Test
    fun serverErrorKeepsTheBuffer() {
        pair()
        buffer(2)
        server.enqueue(
            MockResponse
                .Builder()
                .code(502)
                .body("bad gateway")
                .build(),
        )

        assertEquals(SyncResult.ServerError, sync.upload())
        assertEquals(2, dao.eventCount())
    }

    @Test
    fun emptyBufferMakesNoRequest() {
        pair()

        assertEquals(SyncResult.Done, sync.upload())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun deviceCheckReportsClockSkew() {
        pair()
        val serverTime = NOW_MILLIS / 1_000 + 420
        server.enqueue(
            json(
                200,
                """{"id":"$DEVICE_ID","name":"Pixel 8","platform":"android","server_time":$serverTime}""",
            ),
        )

        assertEquals(DeviceCheck.Ok(420), sync.checkDevice())
        assertTrue(
            server
                .takeRequest()
                .url.encodedPath
                .endsWith("/api/v1/device/me"),
        )
    }

    @Test
    fun deviceCheckUnauthorizedAsksToPairAgain() {
        pair()
        server.enqueue(json(401, """{"error":{"code":"unauthenticated","message":"x"}}"""))

        assertEquals(DeviceCheck.NeedsRepairing, sync.checkDevice())
        assertEquals(SetupState.NeedsRepairing, setup.load())
    }

    private fun ingest(results: String) =
        """{"accepted":0,"duplicate":0,"rejected":0,"results":[$results],"server_time":1800000000}"""

    private fun json(
        code: Int,
        body: String,
    ) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val NOW_MILLIS = 1_800_000_000_000L
        const val DEVICE_ID = "0199aaaa-0000-7000-8000-000000000001"
    }
}
