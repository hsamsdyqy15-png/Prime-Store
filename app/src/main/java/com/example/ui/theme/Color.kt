package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Prime Store Neon Dark Palette
val PrimeBackground = Color(0xFF0B0614)
val PrimeCardBackground = Color(0xFF16101F)
val PrimeCardSurface = Color(0xFF1E162A)
val PrimeSurfaceHover = Color(0xFF281C38)

val NeonPurplePrimary = Color(0xFFA855F7)
val NeonPurpleSecondary = Color(0xFF7C3AED)
val NeonPurpleDeep = Color(0xFF6D28D9)
val NeonPurpleGlow = Color(0x66A855F7)
val NeonBorder = Color(0xFF3B2456)
val NeonBorderGlow = Color(0xFF8B5CF6)

val PrimeTextPrimary = Color(0xFFFFFFFF)
val PrimeTextSecondary = Color(0xFF9E98AB)
val PrimeTextMuted = Color(0xFF6B6478)

val AccentGold = Color(0xFFFBBF24)
val AccentGreen = Color(0xFF10B981)
val AccentRed = Color(0xFFEF4444)
val AccentBlue = Color(0xFF38BDF8)
val AccentPink = Color(0xFFF43F5E)

val PrimeGradientBrush = Brush.horizontalGradient(
    colors = listOf(NeonPurpleSecondary, NeonPurplePrimary)
)

val PrimeCardGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF1C1329), Color(0xFF130D1C))
)

val PrimeGlowBrush = Brush.radialGradient(
    colors = listOf(Color(0x559333EA), Color(0x000B0614))
)
