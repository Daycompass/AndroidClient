package org.daycompass.android.pairing

import android.content.Context
import androidx.core.util.AtomicFile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.daycompass.android.api.ApiResult
import org.daycompass.android.api.DeviceApiClient
import org.daycompass.android.api.DeviceToken
import org.daycompass.android.token.DeviceTokenStore
import org.daycompass.android.token.StoredToken
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException

sealed interface SetupState {
    data object NotPaired : SetupState

    data class Paired(
        val serverUrl: HttpUrl,
        val token: DeviceToken,
    ) : SetupState

    /** A token exists but is unusable, or its server is unknown: pair again, keep the buffer. */
    data object NeedsRepairing : SetupState
}

/**
 * Which server this device is paired with, and its token. Both files live in
 * `noBackupFilesDir`; the server URL is not a secret and is stored as plain text.
 */
class DeviceSetup(
    private val tokenStore: DeviceTokenStore,
    serverFile: File,
    private val clientFor: (HttpUrl) -> DeviceApiClient = { DeviceApiClient(it) },
) {
    private val serverFile = AtomicFile(serverFile)

    @Synchronized
    fun load(): SetupState =
        when (val stored = tokenStore.load()) {
            StoredToken.None -> {
                SetupState.NotPaired
            }

            StoredToken.Unreadable -> {
                SetupState.NeedsRepairing
            }

            is StoredToken.Present -> {
                readServerUrl()?.let { SetupState.Paired(it, stored.token) }
                    ?: SetupState.NeedsRepairing
            }
        }

    /**
     * Pairs with [serverUrl] and stores the result. Blocks; run it off the main thread and do
     * not cancel it halfway, or a consumed code leaves no token behind.
     */
    @Synchronized
    fun pair(
        serverUrl: HttpUrl,
        pairingCode: String,
        deviceName: String,
    ): PairingError? {
        val result = clientFor(serverUrl).pair(pairingCode, deviceName)
        if (result !is ApiResult.Success) return result.toPairingError()
        return try {
            // Drop any old token first: if saving the new one fails, an old token must never
            // end up next to the new server's URL and be sent there.
            tokenStore.clear()
            writeServerUrl(serverUrl)
            tokenStore.save(result.value.token)
            null
        } catch (_: IOException) {
            PairingError.StorageFailed
        } catch (_: GeneralSecurityException) {
            PairingError.StorageFailed
        }
    }

    private fun readServerUrl(): HttpUrl? =
        try {
            serverFile
                .readFully()
                .decodeToString()
                .toHttpUrlOrNull()
                ?.takeIf { it.isHttps }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IOException) {
            null
        }

    private fun writeServerUrl(url: HttpUrl) {
        val out = serverFile.startWrite()
        try {
            out.write(url.toString().encodeToByteArray())
            serverFile.finishWrite(out)
        } catch (e: IOException) {
            serverFile.failWrite(out)
            throw e
        }
    }

    companion object {
        private const val SERVER_FILE = "server_url.txt"

        fun create(context: Context) =
            DeviceSetup(DeviceTokenStore.create(context), File(context.noBackupFilesDir, SERVER_FILE))
    }
}
