package org.daycompass.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.daycompass.android.R
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcColors
import org.daycompass.android.ui.theme.DcRadius
import org.daycompass.android.ui.theme.DcSize
import org.daycompass.android.ui.theme.DcSpacing

/**
 * Single-line input with a label that is always visible above the field, optional helper
 * text and an error state. An error shows an icon and its message, never color alone, and is
 * exposed to accessibility services. Label, helper and error live inside the text field's
 * decoration, so screen readers announce them together with the field.
 */
@Composable
fun DcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = Dc.colors
    val type = Dc.type
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
        enabled = enabled,
        singleLine = true,
        textStyle = type.body.copy(color = if (enabled) colors.text else colors.disabledText),
        cursorBrush = SolidColor(colors.focus),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(DcSpacing.space2)) {
                Text(text = label, style = type.label, color = if (enabled) colors.text else colors.disabledText)
                FieldBox(
                    borderColor = fieldBorderColor(colors, enabled, error != null, focused),
                    showHalo = focused && error == null,
                    enabled = enabled,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = type.body, color = colors.textMuted, maxLines = 1)
                    }
                    innerTextField()
                }
                SupportingText(helper = helper, error = error)
            }
        },
    )
}

private fun fieldBorderColor(
    colors: DcColors,
    enabled: Boolean,
    hasError: Boolean,
    focused: Boolean,
) = when {
    !enabled -> colors.disabledBg
    hasError -> colors.error
    focused -> colors.focus
    else -> colors.borderControl
}

@Composable
private fun FieldBox(
    borderColor: Color,
    showHalo: Boolean,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = Dc.colors
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    FOCUS_HALO,
                    if (showHalo) colors.focus.copy(alpha = HALO_ALPHA) else Color.Transparent,
                    HALO_SHAPE,
                ).padding(FOCUS_HALO)
                .defaultMinSize(minHeight = DcSize.controlMd)
                .background(if (enabled) colors.surface else colors.disabledBg, DcRadius.md)
                .border(DcSize.controlBorder, borderColor, DcRadius.md)
                .padding(horizontal = DcSpacing.space4, vertical = DcSpacing.space3),
    ) {
        content()
    }
}

/** An error replaces the helper text and always comes with an icon. */
@Composable
private fun SupportingText(
    helper: String?,
    error: String?,
) {
    val colors = Dc.colors
    if (error != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(DcSpacing.space1), verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.ic_warning_circle),
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.padding(top = 1.dp).size(ERROR_ICON),
            )
            Text(text = error, style = Dc.type.caption, color = colors.error)
        }
    } else if (helper != null) {
        Text(text = helper, style = Dc.type.caption, color = colors.textMuted)
    }
}

// The halo sits outside the 2dp border and is always laid out, so focus does not shift the layout.
private val FOCUS_HALO = 3.dp
private val HALO_SHAPE = RoundedCornerShape(14.dp + FOCUS_HALO)
private const val HALO_ALPHA = 0.28f
private val ERROR_ICON = 16.dp

@Preview(showBackground = true)
@Composable
private fun DcTextFieldPreview() {
    DaycompassTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(DcSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DcSpacing.space4),
        ) {
            DcTextField(
                value = "",
                onValueChange = {},
                label = "Server URL",
                placeholder = "https://example.org",
                helper = "The address of your Daycompass server.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            DcTextField(
                value = "https://example.org",
                onValueChange = {},
                label = "Server URL",
                error = "Can't reach that server. Check the URL.",
            )
            DcTextField(value = "Pixel 8", onValueChange = {}, label = "Device name", enabled = false)
        }
    }
}
