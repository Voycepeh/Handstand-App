package com.inversioncoach.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val InversionCoachDarkColors = darkColorScheme(
    primary = Color(0xFF66F2FF),
    onPrimary = Color(0xFF002A36),
    primaryContainer = Color(0xFF0E3D57),
    onPrimaryContainer = Color(0xFFD7FBFF),
    secondary = Color(0xFF9DEBFF),
    onSecondary = Color(0xFF062B3A),
    secondaryContainer = Color(0xFF11344A),
    onSecondaryContainer = Color(0xFFD7F5FF),
    tertiary = Color(0xFF7EE6FF),
    onTertiary = Color(0xFF032B37),
    background = Color(0xFF020B1C),
    onBackground = Color(0xFFF3FBFF),
    surface = Color(0xFF071427),
    onSurface = Color(0xFFF3FBFF),
    surfaceVariant = Color(0xFF10263A),
    onSurfaceVariant = Color(0xFFAFC9D8),
    outline = Color(0xFF2A5972),
    outlineVariant = Color(0xFF17384D),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3B0910),
)

@Composable
fun InversionCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InversionCoachDarkColors,
        typography = Typography(),
        content = content,
    )
}
