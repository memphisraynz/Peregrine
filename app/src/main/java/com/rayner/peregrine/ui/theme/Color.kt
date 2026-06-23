package com.rayner.peregrine.ui.theme

import androidx.compose.ui.graphics.Color

// Peregrine Dark Color Palette
val Surface = Color(0xFF141218)
val SurfaceContainer = Color(0xFF211F26)
val SurfaceContainerHigh = Color(0xFF2B2930)
val OnSurface = Color(0xFFE6E0E9)
val OnSurfaceVariant = Color(0xFFCAC4D0)
val Primary = Color(0xFFD0BCFF)
val OnPrimary = Color(0xFF381E72)
val SecondaryContainer = Color(0xFF4A4458)
val OnSecondaryContainer = Color(0xFFE8DEF8)
val Outline = Color(0xFF49454F)
val OutlineVariant = Color(0xFF49454F)

// Functional pops
val AlertBadgeBg = Color(0xFFFFB4AB)
val AlertBadgeText = Color(0xFF690005)
val LiveDot = Color(0xFFFF5449)

// Custom detection palette
object DetectionColors {
    data class Pair(val container: Color, val onContainer: Color, val bright: Color, val onBright: Color)
    val Person  = Pair(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFD0BCFF), Color(0xFF381E72))
    val Vehicle = Pair(Color(0xFF234B6E), Color(0xFFCFE5FF), Color(0xFFA6CBFF), Color(0xFF0A2A4D))
    val Animal  = Pair(Color(0xFF2E4B33), Color(0xFFBCEFBE), Color(0xFF9BE0A0), Color(0xFF0A3311))
    val Verified = Pair(Color(0xFF00513B), Color(0xFFB5F3D1), Color(0xFF34D399), Color(0xFF064E3B))
}
