package org.daycompass.android.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for the device routes of one Daycompass server: pairing, identity and event
 * ingest. Calls block, so run them off the main thread.
 *
 * Redirects are never followed, so the bearer token only ever goes to [baseUrl].
 * TLS uses the system trust store; user-installed CAs are not trusted.
 */
class DeviceApiClient(
    private val baseUrl: HttpUrl,
    httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val httpClient =
        httpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    init {
        require(baseUrl.isHttps) { "the server URL must use https" }
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) {
            "the server URL must not contain credentials"
        }
        require(baseUrl.query == null && baseUrl.fragment == null) {
            "the server URL must not contain a query or fragment"
        }
    }

    /** `POST /api/v1/pair`. [pairingCode] is sent exactly as typed; the server normalizes it. */
    fun pair(
        pairingCode: String,
        deviceName: String,
    ): ApiResult<Pairing> {
        val body = PairRequest(pairingCode = pairingCode, deviceName = deviceName, platform = PLATFORM)
        val request =
            Request
                .Builder()
                .url(endpoint("pair"))
                .post(encode(PairRequest.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
                .build()
        // 401 here is invalid_pairing_code, not a revoked device.
        return execute(request, PairResponse.serializer(), deviceAuth = false) { response ->
            val token = runCatching { DeviceToken(response.deviceToken) }.getOrNull() ?: return@execute null
            Pairing(
                token = token,
                device = response.device.toDevice(),
                serverTime = response.serverTime,
            )
        }
    }

    /** `GET /api/v1/device/me`. Call once at startup for the clock skew check, never poll it. */
    fun me(token: DeviceToken): ApiResult<DeviceIdentity> {
        val request =
            Request
                .Builder()
                .url(endpoint("device/me"))
                .header("Authorization", token.authorizationHeaderValue())
                .get()
                .build()
        return execute(request, DeviceMeResponse.serializer(), deviceAuth = true) { response ->
            DeviceIdentity(
                id = response.id,
                name = response.name,
                platform = response.platform,
                serverTime = response.serverTime,
            )
        }
    }

    /**
     * `POST /api/v1/events`. At most [MAX_BATCH_EVENTS] events and [MAX_BATCH_BYTES] of
     * encoded body; split larger batches before calling. Resending a batch is always safe.
     */
    fun sendEvents(
        token: DeviceToken,
        events: List<Event>,
    ): ApiResult<IngestResult> {
        require(events.isNotEmpty()) { "a batch needs at least one event" }
        require(events.size <= MAX_BATCH_EVENTS) { "a batch holds at most $MAX_BATCH_EVENTS events" }
        val body = encode(IngestRequest.serializer(), IngestRequest(events.map { it.toInput() }))
        require(body.size <= MAX_BATCH_BYTES) { "a batch body is at most $MAX_BATCH_BYTES bytes" }
        val request =
            Request
                .Builder()
                .url(endpoint("events"))
                .header("Authorization", token.authorizationHeaderValue())
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return execute(request, IngestResponse.serializer(), deviceAuth = true) { response ->
            val results = response.results.sortedBy { it.index }
            if (results.map { it.index } != events.indices.toList()) return@execute null
            IngestResult(
                outcomes = results.map { it.toOutcome() ?: return@execute null },
                serverTime = response.serverTime,
            )
        }
    }

    private fun endpoint(path: String): HttpUrl = baseUrl.newBuilder().addPathSegments("api/v1/$path").build()

    private fun <W, T> execute(
        request: Request,
        serializer: KSerializer<W>,
        deviceAuth: Boolean,
        map: (W) -> T?,
    ): ApiResult<T> =
        try {
            httpClient.newCall(request).execute().use { response -> handle(response, serializer, deviceAuth, map) }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }

    private fun <W, T> handle(
        response: Response,
        serializer: KSerializer<W>,
        deviceAuth: Boolean,
        map: (W) -> T?,
    ): ApiResult<T> {
        if (!response.isSuccessful) return failure(response, deviceAuth)
        return decode(serializer, response)?.let(map)?.let { ApiResult.Success(it) } ?: ApiResult.MalformedResponse
    }

    private fun failure(
        response: Response,
        deviceAuth: Boolean,
    ): ApiResult<Nothing> =
        when {
            response.code == HTTP_UNAUTHORIZED && deviceAuth -> {
                ApiResult.Unauthenticated
            }

            response.code == HTTP_TOO_MANY_REQUESTS -> {
                ApiResult.RateLimited(
                    response
                        .header("Retry-After")
                        ?.trim()
                        ?.toLongOrNull()
                        ?.takeIf { it >= 0 },
                )
            }

            else -> {
                ApiResult.ApiError(response.code, decode(ErrorBody.serializer(), response)?.error?.code)
            }
        }

    private fun <W> encode(
        serializer: KSerializer<W>,
        value: W,
    ): ByteArray = json.encodeToString(serializer, value).encodeToByteArray()

    private fun <W> decode(
        serializer: KSerializer<W>,
        response: Response,
    ): W? =
        try {
            json.decodeFromString(serializer, response.body.string())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val MAX_BATCH_EVENTS = 500
        const val MAX_BATCH_BYTES = 512 * 1024

        private const val PLATFORM = "android"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val CALL_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val json =
            Json {
                // Optional fields are left out instead of sent as null; responses may grow fields.
                explicitNulls = false
                ignoreUnknownKeys = true
            }

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}

private fun DeviceView.toDevice() =
    Device(
        id = id,
        name = name,
        platform = platform,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        revokedAt = revokedAt,
    )

private fun Event.toInput() =
    EventInput(
        eventId = eventId,
        startTs = startTs,
        durationSecs = durationSecs,
        appId = appId,
        label = label,
        domain = domain,
    )

private fun EventResultView.toOutcome(): EventOutcome? =
    when (status) {
        "accepted" -> EventOutcome.Accepted
        "duplicate" -> EventOutcome.Duplicate
        "rejected" -> EventOutcome.Rejected(reason)
        else -> null
    }
