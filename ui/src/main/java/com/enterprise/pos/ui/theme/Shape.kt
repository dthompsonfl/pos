package com.enterprise.pos.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * POS Shape System
 *
 * Shapes communicate hierarchy and state. Smaller corner radii feel more
 * precise (chips, badges), while larger radii feel more approachable
 * (cards, dialogs). Full shapes are reserved for avatars and circular controls.
 */
object PosShapes {

    /** 2.dp — Tiny corners for chips, badges, and compact tags. */
    val ExtraSmall = RoundedCornerShape(2.dp)

    /** 4.dp — Small corners for buttons, input fields, and small cards. */
    val Small = RoundedCornerShape(4.dp)

    /** 8.dp — Medium corners for standard cards, dialogs, and snackbars. */
    val Medium = RoundedCornerShape(8.dp)

    /** 12.dp — Large corners for bottom sheets, menus, and dropdowns. */
    val Large = RoundedCornerShape(12.dp)

    /** 16.dp — Extra-large corners for modals, expanded cards, and banners. */
    val ExtraLarge = RoundedCornerShape(16.dp)

    /** 50% — Full circular shape for avatars, profile pictures, and FABs. */
    val Full = CircleShape

    /**
     * Standard Material3 [Shapes] instance for use with [MaterialTheme.shapes].
     * Maps our semantic tokens to the Material3 shape slots.
     */
    val MaterialShapes = Shapes(
        extraSmall = ExtraSmall,
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = ExtraLarge
    )
}
