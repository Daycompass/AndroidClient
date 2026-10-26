package org.daycompass.android.api

import java.io.IOException

/** Outcome of one request. Branch on the type, and for [ApiError] on [ApiError.code]. */
sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    /**
     * `401` on a device route: the token is invalid or the device was revoked. Stop all
     * device requests, keep the buffer and ask the user to pair again. Never retry on a timer.
     */
    data object Unauthenticated : ApiResult<Nothing>

    /**
     * `429`: keep the batch and resend it after [retryAfterSeconds]. `null` when the
     * server sent no usable `Retry-After`.
     */
    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : ApiResult<Nothing>

    /** Any other non-2xx status. [code] is the stable error code, `null` if the body had none. */
    data class ApiError(
        val status: Int,
        val code: String?,
    ) : ApiResult<Nothing>

    /** A 2xx response whose body did not match the wire format. */
    data object MalformedResponse : ApiResult<Nothing>

    data class NetworkError(
        val cause: IOException,
    ) : ApiResult<Nothing>
}
