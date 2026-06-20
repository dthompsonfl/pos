package com.enterprise.pos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * POS Dimension Tokens
 *
 * Single-source-of-truth for all fixed physical dimensions in the POS app.
 * Using these tokens instead of raw `dp` values ensures consistency across
 * modules and makes global sizing adjustments trivial.
 *
 * Categories:
 *   • Touch targets — accessibility-compliant minimum sizes
 *   • Component sizes — buttons, cards, icons, tables
 *   • Layout constants — nav bars, drawers, content max-width
 *   • Elevation — shadows for cards and dialogs
 *   • Borders — hairline, thin, and thick dividers
 */
@Immutable
object PosDimens {

    // =========================================================================
    // Button Dimensions
    // =========================================================================

    /** 48.dp — Standard button height for primary and secondary actions. */
    val ButtonHeight = 48.dp

    /** 56.dp — Large button height for prominent CTAs (e.g., "Pay Now"). */
    val ButtonHeightLarge = 56.dp

    // =========================================================================
    // Icon Sizes
    // =========================================================================

    /** 16.dp — Small icon size for inline indicators and compact list items. */
    val IconSizeSmall = 16.dp

    /** 24.dp — Medium icon size for standard toolbar and button icons. */
    val IconSizeMedium = 24.dp

    /** 32.dp — Large icon size for feature highlights and empty states. */
    val IconSizeLarge = 32.dp

    /** 48.dp — Extra-large icon size for navigation rail and primary actions. */
    val IconSizeExtraLarge = 48.dp

    // =========================================================================
    // Card Elevation
    // =========================================================================

    /** 0.dp — Flat card with no shadow, used for divider-like surfaces. */
    val CardElevationFlat = 0.dp

    /** 1.dp — Subtle elevation for embedded cards within a larger surface. */
    val CardElevationSubtle = 1.dp

    /** 2.dp — Default elevation for standard cards and list items. */
    val CardElevationDefault = 2.dp

    /** 4.dp — Elevated cards that need to float above the background. */
    val CardElevationElevated = 4.dp

    /** 8.dp — High elevation for dialogs, menus, and modal surfaces. */
    val CardElevationHigh = 8.dp

    // =========================================================================
    // Border Widths
    // =========================================================================

    /** 0.5.dp — Hairline border for subtle dividers and disabled states. */
    val BorderWidthHairline = 0.5.dp

    /** 1.dp — Thin border for input fields, cards, and list separators. */
    val BorderWidthThin = 1.dp

    /** 2.dp — Thick border for focused states, selections, and error outlines. */
    val BorderWidthThick = 2.dp

    // =========================================================================
    // Layout & Navigation
    // =========================================================================

    /** 64.dp — Height of the top app bar (toolbar). */
    val AppBarHeight = 64.dp

    /** 80.dp — Height of the bottom navigation bar. */
    val BottomNavHeight = 80.dp

    /** 80.dp — Width of the navigation rail (tablet/desktop layouts). */
    val NavigationRailWidth = 80.dp

    /** 320.dp — Width of the navigation drawer (modal or permanent). */
    val DrawerWidth = 320.dp

    // =========================================================================
    // Accessibility & Touch Targets
    // =========================================================================

    /**
     * 48.dp — Minimum touch target size per WCAG 2.1 / Material Accessibility
     * guidelines. All interactive elements must be at least this size.
     */
    val MinTouchableSize = 48.dp

    // =========================================================================
    // Content Constraints
    // =========================================================================

    /**
     * 840.dp — Maximum content width for large screens (tablets, foldables).
     * Content should not stretch beyond this to maintain readability.
     */
    val MaxContentWidth = 840.dp

    /** 160.dp — Minimum cell width for product grids (catalog, menu items). */
    val GridCellMinWidth = 160.dp

    // =========================================================================
    // Table Dimensions (data tables, order line items, inventory lists)
    // =========================================================================

    /** 56.dp — Standard row height for data tables and list items. */
    val TableRowHeight = 56.dp

    /** 48.dp — Compact header height for data tables to save vertical space. */
    val TableHeaderHeight = 48.dp
}
