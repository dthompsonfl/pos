package com.enterprise.pos.ui.theme

/**
 * POS Font Loading
 *
 * This project uses the **system font stack** (Roboto and Roboto Mono) rather
 * than bundling custom font files. This decision keeps the APK/AAB size small
 * and ensures excellent readability across all Android devices, since Roboto
 * is the default system font on Android and is optimized for screen rendering.
 *
 * If custom brand fonts are required in the future, add them to:
 *   `app/src/main/res/font/`
 *
 * Then create a [FontFamily] here and wire it into [PosTypography] in
 * `Typography.kt`. Example:
 *
 * ```kotlin
 * import androidx.compose.ui.text.font.Font
 * import androidx.compose.ui.text.font.FontFamily
 * import androidx.compose.ui.text.font.FontWeight
 * import com.enterprise.pos.R
 *
 * val Inter = FontFamily(
 *     Font(R.font.inter_regular, FontWeight.Normal),
 *     Font(R.font.inter_medium, FontWeight.Medium),
 *     Font(R.font.inter_semibold, FontWeight.SemiBold),
 *     Font(R.font.inter_bold, FontWeight.Bold)
 * )
 *
 * val RobotoMono = FontFamily(
 *     Font(R.font.roboto_mono_regular, FontWeight.Normal),
 *     Font(R.font.roboto_mono_medium, FontWeight.Medium)
 * )
 * ```
 *
 * Currently, [androidx.compose.ui.text.font.FontFamily.Default] and
 * [androidx.compose.ui.text.font.FontFamily.Monospace] are used throughout the
 * app, which resolve to Roboto and Roboto Mono respectively on all Android
 * devices.
 *
 * Note: For receipt printing (thermal/Epson), the UI font is rendered to a
 * bitmap before printing. The monospaced style (`receiptMono`) ensures that
 * columns align correctly in the on-screen preview before the print job is
 * sent to the printer driver, which typically uses its own embedded bitmap font.
 */

// No custom font definitions needed at this time.
// System fonts (Roboto, Roboto Mono) are used via FontFamily.Default and
// FontFamily.Monospace throughout the theme system.
