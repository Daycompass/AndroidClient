package org.daycompass.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing on a 4dp grid. [space4] is the default padding and screen gutter. */
object DcSpacing {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    val space16 = 64.dp
}

object DcRadius {
    /** Chips, small tags, checkboxes. */
    val sm = RoundedCornerShape(10.dp)

    /** Inputs, small buttons, tooltips. */
    val md = RoundedCornerShape(14.dp)

    /** Buttons, banners. */
    val lg = RoundedCornerShape(16.dp)

    /** Cards, sheets, nav bar. */
    val xl = RoundedCornerShape(20.dp)

    /** Switch tracks, progress bars. */
    val pill = RoundedCornerShape(percent = 50)
}

object DcSize {
    /** Primary call to action at the bottom of a mobile screen. */
    val controlLg = 52.dp

    /** Default button and input height, also the minimum touch target. */
    val controlMd = 44.dp

    /** Depth of the bottom edge on pressable controls; pressing moves them down by this. */
    val edgeDepth = 4.dp

    /** The lighter edge on static cards. */
    val edgeCardDepth = 3.dp

    val iconMd = 24.dp
    val iconSm = 20.dp

    /** Width of the keyboard focus ring, drawn [focusRingOffset] outside the control. */
    val focusRing = 3.dp
    val focusRingOffset = 2.dp

    /** Border of inputs and other controls. */
    val controlBorder = 2.dp
}
