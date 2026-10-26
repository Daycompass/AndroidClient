package org.daycompass.android.api

import java.security.SecureRandom

/** Test values generated at runtime, so no credential-shaped literal lives in the repo. */
object TestValues {
    private val random = SecureRandom()

    fun hex(bytes: Int = 16): String {
        val buffer = ByteArray(bytes).also(random::nextBytes)
        return buffer.joinToString("") { "%02x".format(it) }
    }

    fun tokenValue(): String = "dcd_${hex(32)}"

    /** Shaped like a dashboard code, in lowercase as a person might type it. */
    fun pairingCode(): String = hex(5).take(5) + "-" + hex(5).take(5)
}
