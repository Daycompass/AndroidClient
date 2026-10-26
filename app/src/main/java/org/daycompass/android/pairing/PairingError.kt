package org.daycompass.android.pairing

import androidx.annotation.StringRes
import org.daycompass.android.R
import org.daycompass.android.api.ApiResult
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** The form field an error belongs to; [General] errors show above the button. */
enum class PairingField { ServerUrl, PairingCode, DeviceName, General }

sealed class PairingError(
    val field: PairingField,
    @param:StringRes @get:StringRes val message: Int,
) {
    data object InvalidUrl : PairingError(PairingField.ServerUrl, R.string.pairing_error_invalid_url)

    data object NotHttps : PairingError(PairingField.ServerUrl, R.string.pairing_error_not_https)

    data object Unreachable : PairingError(PairingField.ServerUrl, R.string.pairing_error_unreachable)

    data object UntrustedCertificate : PairingError(
        PairingField.ServerUrl,
        R.string.pairing_error_untrusted_certificate,
    )

    data object Redirected : PairingError(PairingField.ServerUrl, R.string.pairing_error_redirected)

    data object NotDaycompass : PairingError(PairingField.ServerUrl, R.string.pairing_error_not_daycompass)

    data object CodeMissing : PairingError(PairingField.PairingCode, R.string.pairing_error_code_missing)

    /** Unknown, expired, already used or malformed; the server does not say which. */
    data object InvalidCode : PairingError(PairingField.PairingCode, R.string.pairing_error_invalid_code)

    data object InvalidDeviceName : PairingError(PairingField.DeviceName, R.string.pairing_error_invalid_device_name)

    /** `v` in a pairing link was missing or not a version this app understands. */
    data object LinkUnsupportedVersion : PairingError(
        PairingField.General,
        R.string.pairing_error_link_unsupported_version,
    )

    /** A scanned or opened link was not a usable Daycompass pairing link. */
    data object LinkInvalid : PairingError(PairingField.General, R.string.pairing_error_link_invalid)

    /** The code was not consumed: remove a device in the dashboard, then retry the same code. */
    data object DeviceLimitReached : PairingError(PairingField.General, R.string.pairing_error_device_limit)

    /** [message] is the fallback without a delay; the screen words a known delay itself. */
    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : PairingError(PairingField.General, R.string.pairing_error_rate_limited_unknown)

    data object ServerError : PairingError(PairingField.General, R.string.pairing_error_server)

    data object Unexpected : PairingError(PairingField.General, R.string.pairing_error_unexpected)

    /** Pairing worked, but the token could not be stored. The code is used up. */
    data object StorageFailed : PairingError(PairingField.General, R.string.pairing_error_storage)
}

internal fun ApiResult<*>.toPairingError(): PairingError? =
    when (this) {
        is ApiResult.Success -> null

        is ApiResult.RateLimited -> PairingError.RateLimited(retryAfterSeconds)

        is ApiResult.ApiError -> apiErrorToPairingError(status, code)

        is ApiResult.NetworkError -> networkErrorToPairingError(cause)

        // /pair is unauthenticated; a 401 there arrives as ApiError.
        ApiResult.Unauthenticated -> PairingError.Unexpected

        ApiResult.MalformedResponse -> PairingError.NotDaycompass
    }

private const val HTTP_REDIRECT_FIRST = 300
private const val HTTP_REDIRECT_LAST = 399
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVER_ERRORS = 500

private fun apiErrorToPairingError(
    status: Int,
    code: String?,
): PairingError =
    when {
        code == "invalid_pairing_code" -> PairingError.InvalidCode
        code == "device_limit_reached" -> PairingError.DeviceLimitReached
        code == "invalid_device_name" -> PairingError.InvalidDeviceName
        status in HTTP_REDIRECT_FIRST..HTTP_REDIRECT_LAST -> PairingError.Redirected
        status >= HTTP_SERVER_ERRORS -> PairingError.ServerError
        status == HTTP_NOT_FOUND || code == null -> PairingError.NotDaycompass
        else -> PairingError.Unexpected
    }

// OkHttp may wrap the TLS failure or attach it as suppressed after trying another route.
private fun networkErrorToPairingError(cause: IOException): PairingError =
    if (generateSequence<Throwable>(cause) { it.cause }.any(::isTlsFailure)) {
        PairingError.UntrustedCertificate
    } else {
        PairingError.Unreachable
    }

private fun isTlsFailure(error: Throwable): Boolean =
    error is SSLException ||
        error is CertificateException ||
        error.suppressed.any { it is SSLException || it is CertificateException }
