package org.daycompass.android.api

/**
 * The `dcd_...` credential returned once by `POST /api/v1/pair`.
 *
 * The plaintext never shows up in `toString`, logs or debug output. The only way to
 * read it is [authorizationHeaderValue], which exists for the device routes'
 * `Authorization` header and nothing else.
 */
class DeviceToken(
    private val value: String,
) {
    init {
        // The message must not echo the value.
        require(value.length > PREFIX.length && value.startsWith(PREFIX)) { "not a device token" }
    }

    fun authorizationHeaderValue(): String = "Bearer $value"

    override fun toString(): String = "DeviceToken(***)"

    private companion object {
        const val PREFIX = "dcd_"
    }
}
