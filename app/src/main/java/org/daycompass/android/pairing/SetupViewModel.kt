package org.daycompass.android.pairing

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.daycompass.android.buffer.BufferedEvent
import org.daycompass.android.sync.DeviceCheck
import org.daycompass.android.sync.Sync
import org.daycompass.android.sync.SyncResult
import org.daycompass.android.usage.UsageCollection
import org.daycompass.android.usage.hasUsageAccess

/** The pairing form. [toString] leaves the pairing code out so it can never end up in a log. */
data class PairingForm(
    val serverUrl: String = "",
    val pairingCode: String = "",
    val deviceName: String = "",
    val busy: Boolean = false,
    val error: PairingError? = null,
) {
    override fun toString(): String =
        "PairingForm(serverUrl=$serverUrl, deviceName=$deviceName, busy=$busy, error=$error)"
}

sealed interface Screen {
    data object Loading : Screen

    /** [repairing] is true when an earlier pairing became unusable. */
    data class Pairing(
        val repairing: Boolean,
    ) : Screen

    data class Connected(
        val serverHost: String,
        val status: SyncStatus = SyncStatus(),
        val showingBuffer: Boolean = false,
    ) : Screen
}

/** What the connected screen says about syncing, always in words. */
data class SyncStatus(
    val syncing: Boolean = true,
    val lastResult: SyncResult? = null,
    val lastSyncMillis: Long? = null,
    val pendingEvents: Int = 0,
    /** Server clock minus device clock, from the check at app start. */
    val clockSkewSeconds: Long? = null,
)

class SetupViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val setup = DeviceSetup.get(application)

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set

    var form by mutableStateOf(PairingForm(deviceName = defaultDeviceName(Build.MODEL)))
        private set

    /** Whether the user granted usage access; checked again every time the app comes back. */
    var usageAccess by mutableStateOf(hasUsageAccess(application))
        private set

    /** The buffer status screen's list, loaded on demand rather than kept live. */
    var bufferedEvents by mutableStateOf<List<BufferedEvent>>(emptyList())
        private set

    /** Called from the activity's onResume, for example after returning from the settings. */
    fun onResume() {
        val granted = hasUsageAccess(getApplication())
        val newlyGranted = granted && !usageAccess
        usageAccess = granted
        // Show the first data right away instead of waiting for the next background run.
        if (newlyGranted && screen is Screen.Connected) syncNow(checkDevice = false)
    }

    init {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { setup.load() }
            // Collection keeps running while the device waits to be paired again, so
            // nothing is lost in between.
            if (state != SetupState.NotPaired) {
                UsageCollection.schedule(application)
                Sync.schedule(application)
            }
            screen =
                when (state) {
                    SetupState.NotPaired -> Screen.Pairing(repairing = false)
                    SetupState.NeedsRepairing -> Screen.Pairing(repairing = true)
                    is SetupState.Paired -> Screen.Connected(state.serverUrl.host)
                }
            if (state is SetupState.Paired) syncNow(checkDevice = true)
        }
    }

    /**
     * Checks the device once per app start (identity and clock, never on a timer), then
     * collects and uploads right away instead of waiting for the next background run.
     */
    private fun syncNow(checkDevice: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val skew =
                if (checkDevice) {
                    when (val check = withContext(Dispatchers.IO) { Sync.engine(app).checkDevice() }) {
                        is DeviceCheck.Ok -> check.clockSkewSeconds
                        DeviceCheck.NeedsRepairing -> return@launch showRepairing()
                        DeviceCheck.Unknown -> null
                    }
                } else {
                    null
                }
            val result = withContext(Dispatchers.IO) { Sync.runOnce(app) }
            if (result == SyncResult.NeedsRepairing) return@launch showRepairing()
            val status =
                withContext(Dispatchers.IO) {
                    SyncStatus(
                        syncing = false,
                        lastResult = result,
                        lastSyncMillis = Sync.lastSyncMillis(app),
                        pendingEvents = Sync.pendingEvents(app),
                        clockSkewSeconds = skew,
                    )
                }
            (screen as? Screen.Connected)?.let { screen = it.copy(status = status) }
        }
    }

    private fun showRepairing() {
        screen = Screen.Pairing(repairing = true)
    }

    fun showBufferedEvents() {
        val connected = screen as? Screen.Connected ?: return
        screen = connected.copy(showingBuffer = true)
        viewModelScope.launch {
            bufferedEvents = withContext(Dispatchers.IO) { Sync.bufferedEvents(getApplication()) }
        }
    }

    fun hideBufferedEvents() {
        (screen as? Screen.Connected)?.let { screen = it.copy(showingBuffer = false) }
    }

    fun onServerUrlChange(value: String) {
        form = form.copy(serverUrl = value, error = form.error.clearedFor(PairingField.ServerUrl))
    }

    fun onPairingCodeChange(value: String) {
        form = form.copy(pairingCode = value, error = form.error.clearedFor(PairingField.PairingCode))
    }

    fun onDeviceNameChange(value: String) {
        form = form.copy(deviceName = value, error = form.error.clearedFor(PairingField.DeviceName))
    }

    fun submit() {
        val current = form
        if (current.busy) return
        val url = parseServerUrl(current.serverUrl)
        val inputError =
            when {
                url.isFailure -> url.problemError()
                current.pairingCode.isBlank() -> PairingError.CodeMissing
                !isValidDeviceName(current.deviceName) -> PairingError.InvalidDeviceName
                else -> null
            }
        if (inputError != null) {
            form = current.copy(error = inputError)
            return
        }
        form = current.copy(busy = true, error = null)
        viewModelScope.launch {
            // The code is consumed once the server answers, so finish storing the token
            // even if the screen goes away meanwhile.
            val error =
                withContext(Dispatchers.IO + NonCancellable) {
                    setup.pair(url.getOrThrow(), current.pairingCode, current.deviceName.trim()).also {
                        if (it == null) {
                            UsageCollection.start(getApplication())
                            Sync.schedule(getApplication())
                        }
                    }
                }
            if (error == null) {
                form = PairingForm(deviceName = form.deviceName)
                screen = Screen.Connected(url.getOrThrow().host)
                syncNow(checkDevice = false)
            } else {
                form = form.copy(busy = false, error = error)
            }
        }
    }
}

private fun PairingError?.clearedFor(field: PairingField): PairingError? =
    this?.takeUnless { it.field == field || it.field == PairingField.General }

private fun Result<*>.problemError(): PairingError =
    when ((exceptionOrNull() as? ServerUrlException)?.problem) {
        ServerUrlProblem.NotHttps -> PairingError.NotHttps
        else -> PairingError.InvalidUrl
    }
