package org.daycompass.android.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class DeviceApiClientTest {
    private val server = MockWebServer()
    private lateinit var httpClient: OkHttpClient
    private lateinit var client: DeviceApiClient

    @Before
    fun setUp() {
        // A throwaway certificate generated per test run, trusted only by this client.
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server.useHttps(
            HandshakeCertificates
                .Builder()
                .heldCertificate(certificate)
                .build()
                .sslSocketFactory(),
        )
        server.start(InetAddress.getByName("localhost"), 0)
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        httpClient = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        client = DeviceApiClient(server.url("/"), httpClient)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun pairSendsTheCodeAsTypedAndReturnsTheToken() {
        val code = TestValues.pairingCode()
        val secret = TestValues.tokenValue()
        server.enqueue(json(201, pairResponse(secret)))

        val result = client.pair(code, "Pixel 8")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pair", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertJson("""{"pairing_code":"$code","device_name":"Pixel 8","platform":"android"}""", request)
        val pairing = (result as ApiResult.Success).value
        assertEquals("Bearer $secret", pairing.token.authorizationHeaderValue())
        assertEquals("Pixel 8", pairing.device.name)
        assertNull(pairing.device.revokedAt)
        assertEquals(SERVER_TIME, pairing.serverTime)
    }

    @Test
    fun pairTreatsAnInvalidCodeAsAnErrorNotAsRevocation() {
        server.enqueue(error(401, "invalid_pairing_code"))

        assertEquals(ApiResult.ApiError(401, "invalid_pairing_code"), client.pair(TestValues.pairingCode(), "Pixel 8"))
    }

    @Test
    fun pairReportsTheDeviceLimit() {
        server.enqueue(error(409, "device_limit_reached"))

        assertEquals(ApiResult.ApiError(409, "device_limit_reached"), client.pair(TestValues.pairingCode(), "Pixel 8"))
    }

    @Test
    fun pairRejectsAResponseWithoutADeviceToken() {
        server.enqueue(json(201, pairResponse(TestValues.hex())))

        assertEquals(ApiResult.MalformedResponse, client.pair(TestValues.pairingCode(), "Pixel 8"))
    }

    @Test
    fun meSendsTheBearerTokenAndReturnsTheServerTime() {
        val secret = TestValues.tokenValue()
        server.enqueue(
            json(200, """{"id":"$DEVICE_ID","name":"Pixel 8","platform":"android","server_time":$SERVER_TIME}"""),
        )

        val result = client.me(DeviceToken(secret))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/device/me", request.url.encodedPath)
        assertEquals("Bearer $secret", request.headers["Authorization"])
        assertEquals(DeviceIdentity(DEVICE_ID, "Pixel 8", "android", SERVER_TIME), (result as ApiResult.Success).value)
    }

    @Test
    fun deviceRoutesMap401ToUnauthenticated() {
        server.enqueue(error(401, "unauthenticated"))
        server.enqueue(error(401, "unauthenticated"))

        assertEquals(ApiResult.Unauthenticated, client.me(token()))
        assertEquals(ApiResult.Unauthenticated, client.sendEvents(token(), listOf(event())))
    }

    @Test
    fun sendEventsLeavesOutAbsentOptionalFields() {
        val first = event()
        val second = event().copy(label = "Firefox", domain = "example.org")
        server.enqueue(json(200, ingestResponse("""{"index":0,"status":"accepted"},{"index":1,"status":"accepted"}""")))

        client.sendEvents(token(), listOf(first, second))

        val request = server.takeRequest()
        assertEquals("/api/v1/events", request.url.encodedPath)
        assertJson(
            """{"events":[
                {"event_id":"${first.eventId}","start_ts":${first.startTs},"duration_secs":300,"app_id":"org.example.app"},
                {"event_id":"${second.eventId}","start_ts":${second.startTs},"duration_secs":300,
                 "app_id":"org.example.app","label":"Firefox","domain":"example.org"}
            ]}""",
            request,
        )
    }

    @Test
    fun sendEventsMapsResultsByPosition() {
        server.enqueue(
            json(
                200,
                ingestResponse(
                    """{"index":2,"status":"rejected","reason":"duration_out_of_range"},
                       {"index":0,"status":"accepted"},{"index":1,"status":"duplicate"}""",
                ),
            ),
        )

        val result = client.sendEvents(token(), listOf(event(), event(), event()))

        assertEquals(
            IngestResult(
                listOf(
                    EventOutcome.Accepted,
                    EventOutcome.Duplicate,
                    EventOutcome.Rejected("duration_out_of_range"),
                ),
                SERVER_TIME,
            ),
            (result as ApiResult.Success).value,
        )
    }

    @Test
    fun sendEventsRejectsResultsThatDoNotCoverTheBatch() {
        server.enqueue(json(200, ingestResponse("""{"index":0,"status":"accepted"}""")))

        assertEquals(ApiResult.MalformedResponse, client.sendEvents(token(), listOf(event(), event())))
    }

    @Test
    fun sendEventsRejectsAnUnknownStatus() {
        server.enqueue(json(200, ingestResponse("""{"index":0,"status":"maybe"}""")))

        assertEquals(ApiResult.MalformedResponse, client.sendEvents(token(), listOf(event())))
    }

    @Test
    fun sendEventsEnforcesTheBatchSize() {
        val tooMany = List(DeviceApiClient.MAX_BATCH_EVENTS + 1) { event() }

        assertThrows(IllegalArgumentException::class.java) { client.sendEvents(token(), tooMany) }
        assertThrows(IllegalArgumentException::class.java) { client.sendEvents(token(), emptyList()) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rateLimitCarriesRetryAfter() {
        server.enqueue(error(429, "rate_limited").newBuilder().addHeader("Retry-After", "120").build())
        server.enqueue(error(429, "rate_limited"))

        assertEquals(ApiResult.RateLimited(120), client.sendEvents(token(), listOf(event())))
        assertEquals(ApiResult.RateLimited(null), client.me(token()))
    }

    @Test
    fun errorWithoutTheErrorShapeHasNoCode() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(502)
                .body("bad gateway")
                .build(),
        )

        assertEquals(ApiResult.ApiError(502, null), client.me(token()))
    }

    @Test
    fun redirectsAreNotFollowed() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(307)
                .addHeader("Location", server.url("/elsewhere"))
                .build(),
        )

        assertEquals(ApiResult.ApiError(307, null), client.me(token()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun keepsAPathPrefixOfTheServerUrl() {
        server.enqueue(error(401, "unauthenticated"))

        DeviceApiClient(server.url("/daycompass/"), httpClient).me(token())

        assertEquals("/daycompass/api/v1/device/me", server.takeRequest().url.encodedPath)
    }

    @Test
    fun networkFailureIsReported() {
        val url = server.url("/")
        server.close()

        assertTrue(DeviceApiClient(url, httpClient).me(token()) is ApiResult.NetworkError)
    }

    @Test
    fun onlyAcceptsPlainHttpsServerUrls() {
        listOf(
            "http://example.org/",
            "https://alice:secret@example.org/",
            "https://example.org/?a=b",
            "https://example.org/#a",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { DeviceApiClient(url.toHttpUrl(), httpClient) }
        }
    }

    private fun token() = DeviceToken(TestValues.tokenValue())

    private fun event() =
        Event(eventId = TestValues.hex(), startTs = SERVER_TIME - 600, durationSecs = 300, appId = "org.example.app")

    private fun json(
        code: Int,
        body: String,
    ) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun error(
        code: Int,
        errorCode: String,
    ) = json(code, """{"error":{"code":"$errorCode","message":"irrelevant"}}""")

    private fun pairResponse(deviceToken: String) =
        """{"device_token":"$deviceToken","device":{"id":"$DEVICE_ID","name":"Pixel 8","platform":"android",
           "created_at":$SERVER_TIME,"last_seen_at":$SERVER_TIME,"revoked_at":null},"server_time":$SERVER_TIME}"""

    private fun ingestResponse(results: String) =
        """{"accepted":0,"duplicate":0,"rejected":0,"results":[$results],"server_time":$SERVER_TIME}"""

    private fun assertJson(
        expected: String,
        request: RecordedRequest,
    ) {
        val actual: JsonElement = Json.parseToJsonElement(request.body!!.utf8())
        assertEquals(Json.parseToJsonElement(expected), actual)
    }

    private companion object {
        const val SERVER_TIME = 1_800_000_000L
        const val DEVICE_ID = "0199aaaa-0000-7000-8000-000000000001"
    }
}
