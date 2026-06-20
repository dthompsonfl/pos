package com.enterprise.pos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * POS Typography System
 *
 * Built on Material3's 5-scale type system (Display, Headline, Title, Body, Label)
 * with additional POS-specific tokens for prices, receipts, keyboard keys, and
 * navigation labels. All tokens use the system font stack (Roboto) for
 * consistency and accessibility on Android devices.
 *
 * Font weights are chosen to create clear hierarchy:
 *   • Bold (700)    — Primary numbers, prices, totals
 *   • SemiBold (600) — Headlines, section titles
 *   • Medium (500)   — Labels, tab text, navigation
 *   • Normal (400)   — Body text, descriptions
 */
val PosTypography = Typography(

    // =========================================================================
    // Display — Largest type, reserved for hero numbers, splash screens, or
    // prominent data visualizations (e.g., total sales on a dashboard).
    // =========================================================================
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // =========================================================================
    // Headline — Section headers, page titles, and major UI divisions.
    // =========================================================================
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // =========================================================================
    // Title — Card titles, list headers, dialog titles, and form section labels.
    // =========================================================================
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // =========================================================================
    // Body — Primary reading text, descriptions, and multi-line content.
    // Line heights are generous for readability under bright restaurant lighting.
    // =========================================================================
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // =========================================================================
    // Label — Captions, badges, compact buttons, and auxiliary text.
    // =========================================================================
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// =============================================================================
// POS-Specific Typography Tokens
// These are NOT part of Material3's [Typography] but are exposed as extension
// properties for easy access via `PosTheme.typographyExt.priceLarge` etc.
// =============================================================================

/** Largest price display, e.g., grand total on the checkout screen. */
val priceLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 48.sp,
    lineHeight = 56.sp,
    letterSpacing = (-0.5).sp
)

/** Medium price display, e.g., item prices in a product grid. */
val priceMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

/** Small price display, e.g., line-item prices in a cart list. */
val priceSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/** Hero total display for the checkout screen (e.g., "$124.50"). */
val totalDisplay = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 64.sp,
    lineHeight = 72.sp,
    letterSpacing = (-1).sp
)

/** Monospaced receipt text for alignment of prices and quantities. */
val receiptMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/** Keyboard key labels for the on-screen numeric keypad. */
val keyboardKey = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)

/** Tab labels in the bottom or top navigation bar. */
val tabLabel = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

/** Navigation rail / drawer item labels. */
val navLabel = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)
