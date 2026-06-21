package com.enterprise.pos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle

// =============================================================================
// Light Color Scheme (Material3)
// =============================================================================

private val LightColors = lightColorScheme(
    primary = Primary50,
    onPrimary = Color.White,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary10,
    secondary = Secondary50,
    onSecondary = Color.White,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary10,
    tertiary = Tertiary50,
    onTertiary = Color.White,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary10,
    error = Error60,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral100,
    onBackground = Neutral10,
    surface = Color.White,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral40,
    outline = Neutral60,
    outlineVariant = Neutral80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral90,
    inversePrimary = Primary80,
    scrim = Neutral0
)

// =============================================================================
// Dark Color Scheme (Material3)
// =============================================================================

private val DarkColors = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    error = Error70,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral20,
    onSurface = Neutral90,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral70,
    outline = Neutral50,
    outlineVariant = Neutral30,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral10,
    inversePrimary = Primary40,
    scrim = Neutral0
)

// =============================================================================
// Extended Color Scheme (POS-Specific Semantic Colors)
// These live outside Material3's ColorScheme and are accessed via LocalPosColors.
// =============================================================================

@Immutable
data class PosExtendedColors(
    val drawerOpen: Color = DrawerOpen,
    val drawerClosed: Color = DrawerClosed,
    val tableAvailable: Color = TableAvailable,
    val tableOccupied: Color = TableOccupied,
    val tableReserved: Color = TableReserved,
    val tableDirty: Color = TableDirty,
    val orderOpen: Color = OrderOpen,
    val orderPaid: Color = OrderPaid,
    val orderCancelled: Color = OrderCancelled,
    val orderRefunded: Color = OrderRefunded,
    val itemAvailable: Color = ItemAvailable,
    val itemOutOfStock: Color = ItemOutOfStock,
    val itemDiscontinued: Color = ItemDiscontinued,
    val paymentPending: Color = PaymentPending,
    val paymentCompleted: Color = PaymentCompleted,
    val paymentFailed: Color = PaymentFailed,
    val paymentRefunded: Color = PaymentRefunded,
    val success: Color = Success60,
    val onSuccess: Color = Color.White,
    val warning: Color = Warning70,
    val onWarning: Color = Color.Black,
    val info: Color = Info60,
    val onInfo: Color = Color.White
)

// =============================================================================
// Composition Locals
// =============================================================================

/** Provides access to POS-specific extended colors beyond Material3's ColorScheme. */
val LocalPosColors = staticCompositionLocalOf { PosExtendedColors() }

/** Provides access to POS spacing tokens. */
val LocalPosSpacing = staticCompositionLocalOf { PosSpacing }

/** Provides access to POS dimension tokens. */
val LocalPosDimens = staticCompositionLocalOf { PosDimens }

/** Provides access to POS-specific typography tokens (price, receipt, keyboard, etc.). */
val LocalPosTypography = staticCompositionLocalOf { PosTypographyExtensions.DEFAULT }

// =============================================================================
// Window Size Classes (Material3 Adaptive)
// =============================================================================

@Immutable
sealed class WindowSize {
    abstract val maxWidthDp: Int

    /** Phone in portrait, small foldable collapsed. */
    data object Compact : WindowSize() { override val maxWidthDp = 600 }

    /** Phone in landscape, small tablet, foldable partially open. */
    data object Medium : WindowSize() { override val maxWidthDp = 840 }

    /** Large tablet, desktop, foldable fully open. */
    data object Expanded : WindowSize() { override val maxWidthDp = Int.MAX_VALUE }
}

/**
 * Calculates the current window size class based on the available screen width.
 *
 * @param widthDp The current window width in density-independent pixels.
 */
fun calculateWindowSizeClass(widthDp: Int): WindowSize = when {
    widthDp < WindowSize.Compact.maxWidthDp -> WindowSize.Compact
    widthDp < WindowSize.Medium.maxWidthDp -> WindowSize.Medium
    else -> WindowSize.Expanded
}

// =============================================================================
// Theme Helper — Dark Mode Detection
// =============================================================================

/**
 * Returns `true` if the app should render in dark theme.
 *
 * Respects the system setting by default. If the app has a user-level override
 * stored in preferences, that takes precedence.
 *
 * @param darkThemeOverride Optional boolean override from app preferences.
 *                          `null` means "follow system".
 */
@Composable
fun isAppInDarkTheme(darkThemeOverride: Boolean? = null): Boolean =
    darkThemeOverride ?: isSystemInDarkTheme()

// =============================================================================
// POS Theme Composable
// =============================================================================

@Immutable
data class PosTypographyExtensions(
    val priceLarge: TextStyle,
    val priceMedium: TextStyle,
    val priceSmall: TextStyle,
    val totalDisplay: TextStyle,
    val receiptMono: TextStyle,
    val keyboardKey: TextStyle,
    val tabLabel: TextStyle,
    val navLabel: TextStyle
) {
    companion object {
        val DEFAULT = PosTypographyExtensions(
            priceLarge = priceLarge,
            priceMedium = priceMedium,
            priceSmall = priceSmall,
            totalDisplay = totalDisplay,
            receiptMono = receiptMono,
            keyboardKey = keyboardKey,
            tabLabel = tabLabel,
            navLabel = navLabel
        )
    }
}

/**
 * Enterprise POS Theme
 *
 * Wraps Material3 with POS-specific extensions: extended semantic colors,
 * spacing tokens, dimension tokens, and typography extensions. Automatically
 * applies dynamic colors on Android 12+ (API 31+) when available.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param dynamicColor Whether to use the system's dynamic color palette (Android 12+).
 *                   Defaults to `true` on supported devices.
 * @param content The Composable content tree to theme.
 */
@Composable
fun PosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Update system bars to match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // Android 15+ edge-to-edge enforcement
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            } else {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
            }
        }
    }

    CompositionLocalProvider(
        LocalPosColors provides PosExtendedColors(),
        LocalPosSpacing provides PosSpacing,
        LocalPosDimens provides PosDimens,
        LocalPosTypography provides PosTypographyExtensions.DEFAULT
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PosTypography,
            shapes = PosShapes.MaterialShapes,
            content = content
        )
    }
}

// =============================================================================
// Theme Accessor Object
// =============================================================================

/**
 * Convenience object for accessing POS theme tokens without importing
 * multiple CompositionLocals individually.
 */
object PosTheme {
    val colorScheme: androidx.compose.material3.ColorScheme
        @Composable get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable get() = MaterialTheme.typography

    val shapes: androidx.compose.material3.Shapes
        @Composable get() = MaterialTheme.shapes

    val extendedColors: PosExtendedColors
        @Composable get() = LocalPosColors.current

    val spacing: PosSpacing
        @Composable get() = LocalPosSpacing.current

    val dimens: PosDimens
        @Composable get() = LocalPosDimens.current

    val typographyExt: PosTypographyExtensions
        @Composable get() = LocalPosTypography.current
}
