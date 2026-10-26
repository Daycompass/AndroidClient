package org.daycompass.android.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.daycompass.android.R
import org.daycompass.android.ui.components.DcButton
import org.daycompass.android.ui.components.DcButtonVariant
import org.daycompass.android.ui.components.DcTextField
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcSpacing

@Composable
fun PairingLinkConfirmScreen(
    step: PairingStep.Confirming,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val error = step.error?.let { it to it.message() }

    fun errorFor(field: PairingField) = error?.takeIf { it.first.field == field }?.second

    ScreenColumn {
        Image(
            painter = painterResource(R.drawable.dc_symbol),
            contentDescription = null,
            modifier = Modifier.size(SYMBOL_SIZE),
        )
        Text(
            text = stringResource(R.string.pairing_link_confirm_title),
            style = Dc.type.heading1,
            color = Dc.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.pairing_link_confirm_body),
            style = Dc.type.bodyLg,
            color = Dc.colors.textMuted,
        )
        Text(
            text = step.serverUrl.displayHost(),
            style = Dc.type.heading2,
            color = Dc.colors.text,
        )
        DcTextField(
            value = deviceName,
            onValueChange = onDeviceNameChange,
            label = stringResource(R.string.pairing_device_name_label),
            helper = stringResource(R.string.pairing_device_name_helper),
            error = errorFor(PairingField.DeviceName),
            enabled = !step.busy,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
        )
        errorFor(PairingField.General)?.let { GeneralError(it) }
        DcButton(
            text = stringResource(R.string.pairing_submit),
            onClick = onConfirm,
            loading = step.busy,
            large = true,
            modifier = Modifier.fillMaxWidth().padding(top = DcSpacing.space2),
        )
        DcButton(
            text = stringResource(R.string.pairing_link_confirm_cancel),
            onClick = onCancel,
            variant = DcButtonVariant.Neutral,
            enabled = !step.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The host, plus the port only when it is not the scheme's default. */
private fun HttpUrl.displayHost(): String = if (port == HttpUrl.defaultPort(scheme)) host else "$host:$port"

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PairingLinkConfirmScreenPreview() {
    DaycompassTheme(darkTheme = false) {
        PairingLinkConfirmScreen(
            step = PairingStep.Confirming(serverUrl = "https://example.org/".toHttpUrl(), code = "preview"),
            deviceName = "Pixel 8",
            onDeviceNameChange = {},
            onConfirm = {},
            onCancel = {},
        )
    }
}
