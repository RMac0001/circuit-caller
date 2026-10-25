package com.roger.circuitcaller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp

val Background = Color(0xFF14171A)
val Surface = Color(0xFF1C2024)
val Surface2 = Color(0xFF23282D)
val Line = Color(0xFF2E343A)
val TextPrimary = Color(0xFFF2F1EC)
val TextDim = Color(0xFF8B929A)
val Work = Color(0xFFF2C230)
val Rest = Color(0xFF2FBFA0)
val Prep = Color(0xFFE8E6DF)
val Danger = Color(0xFFE8563F)
val RingTrack = Color(0xFF2A2F34)

private val Scheme = darkColorScheme(
    primary = Work,
    onPrimary = Color(0xFF1A1300),
    secondary = Rest,
    onSecondary = Color(0xFF04231C),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    error = Danger,
    outline = Line
)

/**
 * A condensed treatment for timers and names. Bundling a display face would be nicer, but
 * a squeezed sans keeps the app dependency-free and still reads as "gym equipment" rather
 * than "settings dialog".
 */
val DisplayFace = FontFamily.SansSerif
val MonoFace = FontFamily.Monospace

private val condensed = TextGeometricTransform(scaleX = 0.92f)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.Bold,
        fontSize = 68.sp,
        letterSpacing = (-1).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        textGeometricTransform = condensed
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        textGeometricTransform = condensed
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFace,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFace,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun CircuitCallerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content
    )
}
