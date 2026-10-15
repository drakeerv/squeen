package com.drakeerv.squeen.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Uses the device's own font (Roboto / OEM font) instead of bundling a typeface, the
 * system font follows the platform, adapts to accessibility settings and saves ~1 MB of
 * font resources.
 */
val SpeedQueenTypography = Typography()

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
