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
    ) : Screen
}

class SetupViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val setup = DeviceSetup.create(application)

    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set

    var form by mutableStateOf(PairingForm(deviceName = defaultDeviceName(Build.MODEL)))
        private set

    init {
        viewModelScope.launch {
            screen =
                when (val state = withContext(Dispatchers.IO) { setup.load() }) {
                    SetupState.NotPaired -> Screen.Pairing(repairing = false)
                    SetupState.NeedsRepairing -> Screen.Pairing(repairing = true)
                    is SetupState.Paired -> Screen.Connected(state.serverUrl.host)
                }
        }
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
                    setup.pair(url.getOrThrow(), current.pairingCode, current.deviceName.trim())
                }
            if (error == null) {
                form = PairingForm(deviceName = form.deviceName)
                screen = Screen.Connected(url.getOrThrow().host)
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
