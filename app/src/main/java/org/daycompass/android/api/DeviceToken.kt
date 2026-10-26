package org.daycompass.android.api

/**
 * The `dcd_...` credential returned once by `POST /api/v1/pair`.
 *
 * The plaintext never shows up in `toString`, logs or debug output. It can be read in two
 * places only: [authorizationHeaderValue] for the device routes' `Authorization` header, and
 * [plaintextForEncryptedStorage] for the encrypted token store.
 */
class DeviceToken(
    private val value: String,
) {
    init {
        // The message must not echo the value.
        require(value.length > PREFIX.length && value.startsWith(PREFIX)) { "not a device token" }
    }

    fun authorizationHeaderValue(): String = "Bearer $value"

    /** UTF-8 bytes of the token, to be encrypted right away. The caller wipes the array. */
    internal fun plaintextForEncryptedStorage(): ByteArray = value.encodeToByteArray()

    override fun toString(): String = "DeviceToken(***)"

    private companion object {
        const val PREFIX = "dcd_"
    }
}
