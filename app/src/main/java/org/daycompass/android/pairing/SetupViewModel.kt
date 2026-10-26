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
import okhttp3.HttpUrl
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
        val step: PairingStep = PairingStep.ManualEntry,
    ) : Screen

    data class Connected(
        val serverHost: String,
        val status: SyncStatus = SyncStatus(),
        val showingBuffer: Boolean = false,
    ) : Screen
}

/** Which way the person is pairing this device, within [Screen.Pairing]. */
sealed interface PairingStep {
    data object ManualEntry : PairingStep

    data object Scanning : PairingStep

    /** A scanned or opened link, awaiting the mandatory confirmation before it is used. */
    data class Confirming(
        val serverUrl: HttpUrl,
        val code: String,
        val busy: Boolean = false,
        val error: PairingError? = null,
    ) : PairingStep {
        override fun toString(): String = "Confirming(serverUrl=$serverUrl, busy=$busy, error=$error)"
    }
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

// One cohesive state machine for the pairing screen's sub-flows (manual entry, scanning, link
// confirmation) sharing the same `screen`/`form` state; splitting it would scatter that state
// across collaborating classes for no behavioral benefit.
@Suppress("TooManyFunctions")
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

    /** A link that arrived before [screen] left [Screen.Loading]; applied once it does. */
    private var pendingLinkUri: String? = null

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
            pendingLinkUri?.let(::onLinkScanned)
            pendingLinkUri = null
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
        val deviceName = current.deviceName.trim()
        viewModelScope.launch {
            val error = performPair(setup, getApplication(), url.getOrThrow(), current.pairingCode, deviceName)
            if (error == null) {
                form = PairingForm(deviceName = form.deviceName)
                screen = Screen.Connected(url.getOrThrow().host)
                syncNow(checkDevice = false)
            } else {
                form = form.copy(busy = false, error = error)
            }
        }
    }

    /** Shows the in-app scanner; a no-op unless the person is on the manual pairing step. */
    fun startScanning() {
        (screen as? Screen.Pairing)?.let { screen = it.copy(step = PairingStep.Scanning) }
    }

    /** Leaves scanning or a pending confirmation and goes back to manual entry. */
    fun cancelPairingStep() {
        val pairing = screen as? Screen.Pairing ?: return
        if ((pairing.step as? PairingStep.Confirming)?.busy == true) return
        screen = pairing.copy(step = PairingStep.ManualEntry)
    }

    /**
     * Called with the raw link text from the QR scanner or a `daycompass://pair` intent. Only
     * ever moves to a confirmation step or shows an error - never pairs by itself.
     */
    fun onLinkScanned(raw: String) {
        if (screen is Screen.Loading) {
            pendingLinkUri = raw
            return
        }
        // This app has no "replace the current pairing" flow; the only existing re-pair path
        // is NeedsRepairing, reached only when the server itself revokes the token. Acting on
        // a stray link while already paired is out of scope here, not an oversight.
        val pairing = screen as? Screen.Pairing ?: return
        when (val outcome = interpretPairingLink(raw)) {
            is PairingLinkOutcome.NeedsConfirmation -> {
                screen = pairing.copy(step = PairingStep.Confirming(outcome.serverUrl, outcome.code))
            }

            is PairingLinkOutcome.Rejected -> {
                screen = pairing.copy(step = PairingStep.ManualEntry)
                form = form.copy(error = outcome.error)
            }
        }
    }

    fun confirmLinkPairing() {
        val pairing = screen as? Screen.Pairing ?: return
        val confirming = pairing.step as? PairingStep.Confirming
        when {
            confirming == null || confirming.busy -> {
                return
            }

            !isValidDeviceName(form.deviceName) -> {
                screen = pairing.copy(step = confirming.copy(error = PairingError.InvalidDeviceName))
            }

            else -> {
                screen = pairing.copy(step = confirming.copy(busy = true, error = null))
                val deviceName = form.deviceName.trim()
                viewModelScope.launch {
                    val error =
                        performPair(setup, getApplication(), confirming.serverUrl, confirming.code, deviceName)
                    if (error == null) {
                        form = PairingForm(deviceName = form.deviceName)
                        screen = Screen.Connected(confirming.serverUrl.host)
                        syncNow(checkDevice = false)
                    } else {
                        setConfirmingResult(busy = false, error = error)
                    }
                }
            }
        }
    }

    /** Updates the current `Confirming` step in place, if the screen hasn't moved on already. */
    private fun setConfirmingResult(
        busy: Boolean,
        error: PairingError?,
    ) {
        val pairing = screen as? Screen.Pairing ?: return
        val confirming = pairing.step as? PairingStep.Confirming ?: return
        screen = pairing.copy(step = confirming.copy(busy = busy, error = error))
    }
}

private fun PairingError?.clearedFor(field: PairingField): PairingError? =
    this?.takeUnless { it.field == field || it.field == PairingField.General }

private fun Result<*>.problemError(): PairingError =
    when ((exceptionOrNull() as? ServerUrlException)?.problem) {
        ServerUrlProblem.NotHttps -> PairingError.NotHttps
        else -> PairingError.InvalidUrl
    }

/**
 * Pairs and stores the result. The code is consumed once the server answers, so this
 * finishes even if the screen goes away meanwhile.
 */
private suspend fun performPair(
    setup: DeviceSetup,
    application: Application,
    serverUrl: HttpUrl,
    pairingCode: String,
    deviceName: String,
): PairingError? =
    withContext(Dispatchers.IO + NonCancellable) {
        setup.pair(serverUrl, pairingCode, deviceName).also {
            if (it == null) {
                UsageCollection.start(application)
                Sync.schedule(application)
            }
        }
    }
