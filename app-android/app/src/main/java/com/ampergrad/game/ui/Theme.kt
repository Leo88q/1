package com.ampergrad.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра Амперграда (см. docs/05-ampergrad-bible.md). */
object Grad {
    val Bg = Color(0xFF06080E)
    val BgTop = Color(0xFF0B0F1A)
    val Ink = Color(0xFFD7E3F4)
    val InkDim = Color(0xFF6F8199)
    val Line = Color(0xFF1B2637)
    val Panel = Color(0xFF0D1421)
    val Mint = Color(0xFF00E5C7)
    val MintDeep = Color(0xFF00B8A0)
    val Gold = Color(0xFFFFC24B)
    val Danger = Color(0xFFFF3D81)
    val Metal = Color(0xFF26334A)
    val Hub = Color(0xFF16202F)
}

/** Ярусы: название и акцентный цвет (5 ярусов × 15 уровней). */
data class Tier(val name: String, val accent: Color)

val TIERS = listOf(
    Tier("ПЛАТО ПЕРВОГО КОНТАКТА", Color(0xFF00E5C7)),
    Tier("РТУТНЫЙ ЯРУС", Color(0xFF2AD9C4)),
    Tier("ТЕРМИНАЛЬНЫЙ ЯРУС", Color(0xFF5CC8FF)),
    Tier("СТАРЫЙ ДИОД", Color(0xFFFFC24B)),
    Tier("ПЛАВИЛЬНЯ", Color(0xFFFF3D81)),
)

fun tierOf(levelNumber: Int): Tier = TIERS[((levelNumber - 1) / 15).coerceIn(0, TIERS.size - 1)]

private val AmperTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
    ),
)

@Composable
fun AmpergradTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Grad.Mint,
            onPrimary = Color(0xFF04211D),
            secondary = Grad.Gold,
            background = Grad.Bg,
            onBackground = Grad.Ink,
            surface = Grad.Panel,
            onSurface = Grad.Ink,
            error = Grad.Danger,
        ),
        typography = AmperTypography,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Grad.BgTop, Grad.Bg))),
        ) {
            content()
        }
    }
}
