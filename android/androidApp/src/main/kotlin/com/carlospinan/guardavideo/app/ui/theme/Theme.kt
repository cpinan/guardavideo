package com.carlospinan.guardavideo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.carlospinan.guardavideo.app.settings.ThemeMode

/** The web app's tokens (frontend/src/styles.css), so both apps look like one product. */
@Immutable
data class BrandColors(
    val bg: Color,
    val bgGlow: Color,
    val card: Color,
    val card2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accent2: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
) {
    val accentGradient: Brush get() = Brush.linearGradient(listOf(accent, accent2))
}

private val LightBrand = BrandColors(
    bg = Color(0xFFF4F6FB), bgGlow = Color(0xFFDFE8FF), card = Color.White, card2 = Color(0xFFF0F3FA),
    text = Color(0xFF121826), muted = Color(0xFF5D6679), line = Color(0xFFD9DEEA),
    accent = Color(0xFF3B6CF6), accent2 = Color(0xFF7C4DFF), ok = Color(0xFF1F9D55), warn = Color(0xFFD98206), err = Color(0xFFD33A2C),
)

private val DarkBrand = BrandColors(
    bg = Color(0xFF0D111B), bgGlow = Color(0xFF1A2450), card = Color(0xFF151B2B), card2 = Color(0xFF1C2438),
    text = Color(0xFFE8ECF6), muted = Color(0xFF97A1B7), line = Color(0xFF28324A),
    accent = Color(0xFF6C8CFF), accent2 = Color(0xFFA07DFF), ok = Color(0xFF3ECF80), warn = Color(0xFFF5A524), err = Color(0xFFFF6B5E),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** Same corner radius as the web cards (16px) and its pills. */
private val BrandShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun GuardavideoTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val brand = if (dark) DarkBrand else LightBrand
    val scheme = if (dark) {
        darkColorScheme(
            primary = brand.accent, onPrimary = Color.White, secondary = brand.accent2, tertiary = brand.warn,
            background = brand.bg, onBackground = brand.text, surface = brand.card, onSurface = brand.text,
            surfaceVariant = brand.card2, onSurfaceVariant = brand.muted, outline = brand.line, outlineVariant = brand.line,
            surfaceContainer = brand.card, surfaceContainerHigh = brand.card2, surfaceContainerHighest = brand.card2,
            error = brand.err, errorContainer = brand.err.copy(alpha = 0.18f), onErrorContainer = brand.text,
        )
    } else {
        lightColorScheme(
            primary = brand.accent, onPrimary = Color.White, secondary = brand.accent2, tertiary = brand.warn,
            background = brand.bg, onBackground = brand.text, surface = brand.card, onSurface = brand.text,
            surfaceVariant = brand.card2, onSurfaceVariant = brand.muted, outline = brand.line, outlineVariant = brand.line,
            surfaceContainer = brand.card, surfaceContainerHigh = brand.card2, surfaceContainerHighest = brand.card2,
            error = brand.err, errorContainer = brand.err.copy(alpha = 0.12f), onErrorContainer = brand.text,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, shapes = BrandShapes, content = content)
    }
}
