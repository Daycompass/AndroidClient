package org.daycompass.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.daycompass.android.R
import org.daycompass.android.ui.theme.DaycompassTheme
import org.daycompass.android.ui.theme.Dc
import org.daycompass.android.ui.theme.DcColors
import org.daycompass.android.ui.theme.DcSize
import org.daycompass.android.ui.theme.DcSpacing

/**
 * The chunky Daycompass button: a solid bottom edge that collapses when pressed. Disabled
 * buttons sit already pressed and lose the edge, so the state never relies on color alone.
 * While [loading], a spinner replaces the label and the button keeps its width.
 */
@Composable
fun DcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DcButtonVariant = DcButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    large: Boolean = false,
) {
    val colors = Dc.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val look = colors.look(variant, enabled, pressed)
    val faceHeight = if (large) DcSize.controlLg else DcSize.controlMd
    val drop by animateDpAsState(
        targetValue = if (pressed || !enabled) DcSize.edgeDepth else 0.dp,
        animationSpec = tween(durationMillis = PRESS_MILLIS),
        label = "buttonDrop",
    )
    val loadingDescription = stringResource(R.string.button_loading)

    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = DcSize.controlMd)
                .height(faceHeight + DcSize.edgeDepth)
                .focusRing(focused, colors.focus)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { if (loading) stateDescription = loadingDescription }
                .edgeAndFace(look) { drop },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .offset { IntOffset(0, drop.roundToPx()) }
                    .fillMaxWidth()
                    .height(faceHeight)
                    .then(look.outline?.let { Modifier.border(DcSize.controlBorder, it, SHAPE) } ?: Modifier)
                    .padding(horizontal = if (large) DcSpacing.space6 else DcSpacing.space5),
        ) {
            // The label stays laid out while loading so the width does not jump.
            Text(
                text = text,
                style = Dc.type.button,
                color = if (loading) Color.Transparent else look.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(DcSize.iconSm),
                    color = look.content,
                    strokeWidth = SPINNER_STROKE,
                )
            }
        }
    }
}

/** What a button looks like in its current state. A `null` edge means it is collapsed. */
private data class ButtonLook(
    val fill: Color,
    val content: Color,
    val edge: Color?,
    val outline: Color?,
)

private data class Palette(
    val fill: Color,
    val pressedFill: Color,
    val content: Color,
    val edge: Color,
)

private fun DcColors.look(
    variant: DcButtonVariant,
    enabled: Boolean,
    pressed: Boolean,
): ButtonLook {
    if (!enabled) return ButtonLook(fill = disabledBg, content = disabledText, edge = null, outline = null)
    val palette =
        when (variant) {
            DcButtonVariant.Primary -> Palette(primary, primaryPressed, onPrimary, primaryEdge)
            DcButtonVariant.Secondary -> Palette(secondary, secondaryPressed, onSecondary, secondaryEdge)
            DcButtonVariant.Neutral -> Palette(surface, surfaceSunken, text, neutralEdge)
        }
    return ButtonLook(
        fill = if (pressed) palette.pressedFill else palette.fill,
        content = palette.content,
        edge = palette.edge.takeUnless { pressed },
        outline = neutralEdge.takeIf { variant == DcButtonVariant.Neutral },
    )
}

/** Draws the bottom edge and, [drop] lower while pressed, the face on top of it. */
private fun Modifier.edgeAndFace(
    look: ButtonLook,
    drop: () -> Dp,
): Modifier =
    drawBehind {
        val depth = DcSize.edgeDepth.toPx()
        val faceSize = Size(size.width, size.height - depth)
        val corner = CornerRadius(CORNER.toPx())
        look.edge?.let { drawRoundRect(it, topLeft = Offset(0f, depth), size = faceSize, cornerRadius = corner) }
        drawRoundRect(look.fill, topLeft = Offset(0f, drop().toPx()), size = faceSize, cornerRadius = corner)
    }

/** Keyboard focus ring: drawn outside the control, never removed. */
private fun Modifier.focusRing(
    visible: Boolean,
    color: Color,
): Modifier =
    if (visible) {
        drawBehind {
            val inset = -(DcSize.focusRingOffset + DcSize.focusRing / 2).toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius((CORNER + DcSize.focusRingOffset).toPx()),
                style = Stroke(DcSize.focusRing.toPx()),
            )
        }
    } else {
        this
    }

// radius-lg
private val CORNER = 16.dp
private val SHAPE = RoundedCornerShape(CORNER)
private val SPINNER_STROKE = 2.5.dp
private const val PRESS_MILLIS = 80

@Preview(showBackground = true)
@Composable
private fun DcButtonPreview() {
    DaycompassTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(DcSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DcSpacing.space2),
        ) {
            DcButton("Connect server", onClick = {}, large = true, modifier = Modifier.fillMaxWidth())
            DcButton("Start focus", onClick = {}, variant = DcButtonVariant.Secondary)
            DcButton("Later", onClick = {}, variant = DcButtonVariant.Neutral)
            DcButton("Set goal", onClick = {}, enabled = false)
            DcButton("Set goal", onClick = {}, loading = true)
        }
    }
}
