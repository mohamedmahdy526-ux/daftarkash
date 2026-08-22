package com.example.daftarkash.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Unified Premium Brand Identity: "DaftarKash Indigo Teal"
// Deep Midnight Slate for Dark Mode, Crisp Paper White for Light Mode
val BrandPrimary = Color(0xFF0D9488)        // Refined Deep Teal / Emerald Green
val BrandPrimaryDark = Color(0xFF0F766E)    // Rich Teal for Light Mode contrast
val BrandPrimaryLight = Color(0xFF14B8A6)   // Vibrant Mint Teal for Dark Mode highlights

// Backgrounds & Surfaces
val DarkBg = Color(0xFF0B0F19)              // Ultra-deep clean slate OLED
val DarkSurface = Color(0xFF111827)         // Refined card surface
val DarkSurfaceVariant = Color(0xFF1F2937)  // Elevated chip/input container
val DarkOutline = Color(0xFF374151)         // Subtle crisp border

val LightBg = Color(0xFFEDF2F7)             // Soft Warm Slate Grey (Adds natural 3D depth against white cards)
val LightSurface = Color(0xFFFFFFFF)        // Crisp Pure White Card
val LightSurfaceVariant = Color(0xFFE2E8F0)  // Gentle soft grey container
val LightOutline = Color(0xFFCBD5E1)        // Clean subtle border

// Legacy compatibility references
val Emerald500 = BrandPrimary
val Emerald600 = BrandPrimaryDark
val Emerald400 = BrandPrimaryLight
val Sapphire500 = BrandPrimary
val Sapphire600 = BrandPrimaryDark
val Sapphire400 = BrandPrimaryLight
val Violet500 = BrandPrimary
val Violet600 = BrandPrimaryDark
val Violet400 = BrandPrimaryLight
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFD97706)
val Amber400 = Color(0xFFFBBF24)
val Rose500 = Color(0xFFF43F5E)
val Rose600 = Color(0xFFE11D48)

// Status & Semantic Colors (Rich, Vivid, High-Contrast)
val DangerRed = Color(0xFFE11D48)           // Rich Vivid Carmine/Crimson Red (Punchy & Ultra Clear)
val DangerRedDark = Color(0xFFBE123C)       // Deep Crimson for Light Mode backgrounds
val DangerRedLight = Color(0xFFFDA4AF)      // Soft Rose highlight
val SuccessGreen = Color(0xFF059669)        // Deep Emerald Forest Green (High-Contrast & Crisp)
val SuccessGreenDark = Color(0xFF047857)    // Deep Forest Green for dark contrast
val SuccessGreenLight = Color(0xFF6EE7B7)    // Soft Mint highlight
val WarningYellow = Color(0xFFD97706)       // Rich Amber

@Composable
fun DaftarKashTheme(
    darkTheme: Boolean = true,
    brandTheme: String = "emerald",
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val primaryColor = if (darkTheme) BrandPrimaryLight else BrandPrimary
    val primaryContainerColor = if (darkTheme) BrandPrimary.copy(alpha = 0.22f) else BrandPrimary.copy(alpha = 0.12f)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = Color(0xFFCCFBF1),
            secondary = Color(0xFF38BDF8),
            onSecondary = Color.Black,
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = Color(0xFFF1F5F9),
            background = DarkBg,
            onBackground = Color(0xFFF9FAFB),
            surface = DarkSurface,
            onSurface = Color(0xFFF9FAFB),
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = Color(0xFF9CA3AF),
            error = DangerRed,
            onError = Color.White,
            outline = DarkOutline,
            outlineVariant = Color(0xFF1F2937)
        )
    } else {
        lightColorScheme(
            primary = BrandPrimary,
            onPrimary = Color.White,
            primaryContainer = primaryContainerColor,
            onPrimaryContainer = BrandPrimaryDark,
            secondary = Color(0xFF0284C7),
            onSecondary = Color.White,
            secondaryContainer = LightSurfaceVariant,
            onSecondaryContainer = Color(0xFF0F172A),
            background = LightBg,
            onBackground = Color(0xFF0F172A),
            surface = LightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = Color(0xFF475569),
            error = DangerRed,
            onError = Color.White,
            outline = LightOutline,
            outlineVariant = Color(0xFFE2E8F0)
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = (28 * fontScale).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * fontScale).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18 * fontScale).sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = (17 * fontScale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * fontScale).sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (13 * fontScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * fontScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * fontScale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (11 * fontScale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = (13 * fontScale).sp
        )
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // In Dark Mode -> Light status bar icons (white icons & battery on dark background)
                // In Light Mode -> Dark status bar icons (dark icons & battery on light background)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
