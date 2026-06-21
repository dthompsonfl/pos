package com.enterprise.pos.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * POS Spacing System
 *
 * A uniform 4.dp-based scale (4, 8, 12, 16, 24, 32, 48, 64, 96, 128) ensures
 * consistent rhythm across all UI components. All spacing values are
 * multiples of 4.dp to align with the Material3 grid and Android's
 * density-independent pixel system.
 *
 * Usage:
 *   • Modifier.padding(Space4)          — 16.dp padding
 *   • Modifier.padding(horizontal = Space4, vertical = Space2)
 *   • Column(verticalArrangement = Arrangement.spacedBySpace(2))
 */
@Immutable
object PosSpacing {

    /** 0.dp — No spacing. Useful for resetting default padding. */
    val Space0: Dp = 0.dp

    /** 4.dp — Micro spacing. Tight internal padding, icon gutters. */
    val Space1: Dp = 4.dp

    /** 8.dp — Small spacing. Button internal padding, compact list gaps. */
    val Space2: Dp = 8.dp

    /** 12.dp — Compact spacing. Card internal padding, form field gaps. */
    val Space3: Dp = 12.dp

    /** 16.dp — Standard spacing. Default card padding, screen edge margins. */
    val Space4: Dp = 16.dp

    /** 24.dp — Medium spacing. Section breaks, dialog padding. */
    val Space5: Dp = 24.dp

    /** 32.dp — Large spacing. Major section dividers, expanded card padding. */
    val Space6: Dp = 32.dp

    /** 48.dp — Extra-large spacing. Hero section padding, large gaps. */
    val Space7: Dp = 48.dp

    /** 64.dp — Huge spacing. Dashboard module separation, landing gaps. */
    val Space8: Dp = 64.dp

    /** 96.dp — Massive spacing. Full-screen layouts, splash padding. */
    val Space9: Dp = 96.dp

    /** 128.dp — Maximum spacing. Used sparingly for extreme visual breathing room. */
    val Space10: Dp = 128.dp

    /**
     * Maps an integer index (0..10) to the corresponding spacing token.
     * Falls back to [Space4] (16.dp) for out-of-range values.
     */
    fun fromIndex(index: Int): Dp = when (index) {
        0 -> Space0
        1 -> Space1
        2 -> Space2
        3 -> Space3
        4 -> Space4
        5 -> Space5
        6 -> Space6
        7 -> Space7
        8 -> Space8
        9 -> Space9
        10 -> Space10
        else -> Space4
    }
}

// =============================================================================
// Modifier Extensions
// =============================================================================

/**
 * Applies uniform padding using the POS spacing system index.
 *
 * @param space Index into the spacing scale (0..10). See [PosSpacing.fromIndex].
 */
fun Modifier.padding(space: Int): Modifier =
    padding(PosSpacing.fromIndex(space))

/**
 * Applies horizontal padding using the POS spacing system index.
 *
 * @param space Index into the spacing scale (0..10).
 */
fun Modifier.paddingHorizontal(space: Int): Modifier =
    padding(horizontal = PosSpacing.fromIndex(space))

/**
 * Applies vertical padding using the POS spacing system index.
 *
 * @param space Index into the spacing scale (0..10).
 */
fun Modifier.paddingVertical(space: Int): Modifier =
    padding(vertical = PosSpacing.fromIndex(space))

// =============================================================================
// Arrangement Helpers
// =============================================================================

/**
 * Creates a vertical/horizontal arrangement with a gap defined by the POS
 * spacing system index.
 *
 * Example:
 * ```kotlin
 * Column(
 *     verticalArrangement = Arrangement.spacedBySpace(2)
 * ) { ... }
 * ```
 *
 * @param space Index into the spacing scale (0..10).
 */
fun Arrangement.spacedBySpace(space: Int): Arrangement.Vertical =
    Arrangement.spacedBy(PosSpacing.fromIndex(space))

/**
 * Horizontal variant of [spacedBySpace] for use in [Row] composables.
 *
 * @param space Index into the spacing scale (0..10).
 */
fun Arrangement.spacedBySpaceHorizontal(space: Int): Arrangement.Horizontal =
    Arrangement.spacedBy(PosSpacing.fromIndex(space))
