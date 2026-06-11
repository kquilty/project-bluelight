package com.projectbluelight.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bluelight is permanently a night-sky app — no light theme. The palette is a
// deep navy field with one luminous blue that marks "this matters now".
val Abyss = Color(0xFF060D17)
val DeepSpace = Color(0xFF0A1422)
val Surface1 = Color(0xFF101F33)
val Surface2 = Color(0xFF16283F)
val Accent = Color(0xFF4DA3FF)
val AccentGlow = Color(0xFF8CC5FF)
val OnAccent = Color(0xFF051120)
val Ink = Color(0xFFE7EEF7)
val InkDim = Color(0xFF93A7C0)
val InkFaint = Color(0xFF5C7089)

private val BluelightColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Surface2,
    onPrimaryContainer = AccentGlow,
    secondary = InkDim,
    onSecondary = DeepSpace,
    secondaryContainer = Surface2,
    onSecondaryContainer = Ink,
    background = DeepSpace,
    onBackground = Ink,
    surface = Surface1,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = InkDim,
    outline = InkFaint,
)

private val BluelightType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 2.sp),
    )
}

@Composable
fun BluelightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BluelightColors,
        typography = BluelightType,
        content = content,
    )
}
