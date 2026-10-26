package org.daycompass.android.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.daycompass.android.R
import org.daycompass.android.ui.components.DcButton
import org.daycompass.android.ui.components.DcTextField
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcRadius
import org.daycompass.android.ui.theme.DcSize
import org.daycompass.android.ui.theme.DcSpacing

@Composable
fun PairingScreen(
    form: PairingForm,
    repairing: Boolean,
    onServerUrlChange: (String) -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    val error = form.error?.let { it to it.message() }

    fun errorFor(field: PairingField) = error?.takeIf { it.first.field == field }?.second

    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.dc_symbol),
            contentDescription = null,
            modifier = Modifier.size(SYMBOL_SIZE),
        )
        Text(
            text = stringResource(if (repairing) R.string.pairing_repair_title else R.string.pairing_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(if (repairing) R.string.pairing_repair_body else R.string.pairing_intro),
            style = Dc.type.bodyLg,
            color = Dc.colors.textMuted,
        )
        DcTextField(
            value = form.serverUrl,
            onValueChange = onServerUrlChange,
            label = stringResource(R.string.pairing_server_url_label),
            placeholder = stringResource(R.string.pairing_server_url_placeholder),
            helper = stringResource(R.string.pairing_server_url_helper),
            error = errorFor(PairingField.ServerUrl),
            enabled = !form.busy,
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions = next,
        )
        DcTextField(
            value = form.pairingCode,
            onValueChange = onPairingCodeChange,
            label = stringResource(R.string.pairing_code_label),
            placeholder = stringResource(R.string.pairing_code_placeholder),
            error = errorFor(PairingField.PairingCode),
            enabled = !form.busy,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions = next,
        )
        DcTextField(
            value = form.deviceName,
            onValueChange = onDeviceNameChange,
            label = stringResource(R.string.pairing_device_name_label),
            helper = stringResource(R.string.pairing_device_name_helper),
            error = errorFor(PairingField.DeviceName),
            enabled = !form.busy,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                }),
        )
        errorFor(PairingField.General)?.let { GeneralError(it) }
        DcButton(
            text = stringResource(R.string.pairing_submit),
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            loading = form.busy,
            large = true,
            modifier = Modifier.fillMaxWidth().padding(top = DcSpacing.space2),
        )
    }
}

@Composable
fun ConnectedScreen(serverHost: String) {
    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.dc_symbol),
            contentDescription = null,
            modifier = Modifier.size(SYMBOL_SIZE),
        )
        Text(
            text = stringResource(R.string.connected_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.connected_body, serverHost),
            style = Dc.type.bodyLg,
            color = Dc.colors.text,
        )
        Text(
            text = stringResource(R.string.connected_next),
            style = Dc.type.body,
            color = Dc.colors.textMuted,
        )
    }
}

/** Scrolls, keeps clear of system bars and the keyboard, and caps the width on tablets. */
@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DcSpacing.space4),
            modifier =
                Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .padding(horizontal = DcSpacing.space4, vertical = DcSpacing.space8),
        ) {
            content()
        }
    }
}

/** An error that belongs to no single field. Announced right away, shown with an icon. */
@Composable
private fun GeneralError(message: String) {
    val colors = Dc.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(DcSpacing.space2),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.errorSoft, DcRadius.lg)
                .padding(DcSpacing.space4)
                .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning_circle),
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(DcSize.iconSm),
        )
        Text(text = message, style = Dc.type.body, color = colors.text)
    }
}

@Composable
private fun PairingError.message(): String {
    val minutes = (this as? PairingError.RateLimited)?.let { retryAfterMinutes(it.retryAfterSeconds) }
    return if (minutes != null) {
        pluralStringResource(R.plurals.pairing_error_rate_limited, minutes, minutes)
    } else {
        stringResource(message)
    }
}

/** Whole minutes to wait, rounded up and at least one; `null` when the server gave no delay. */
internal fun retryAfterMinutes(seconds: Long?): Int? =
    seconds?.let { ((it + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1).toInt() }

private const val SECONDS_PER_MINUTE = 60L
private val SYMBOL_SIZE = 56.dp
private val MAX_CONTENT_WIDTH = 480.dp

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PairingScreenPreview() {
    DaycompassTheme(darkTheme = false) {
        PairingScreen(
            form = PairingForm(serverUrl = "example.org", deviceName = "Pixel 8", error = PairingError.InvalidCode),
            repairing = false,
            onServerUrlChange = {},
            onPairingCodeChange = {},
            onDeviceNameChange = {},
            onSubmit = {},
        )
    }
}
