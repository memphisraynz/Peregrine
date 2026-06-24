package com.rayner.peregrine.ui.theme

import androidx.compose.ui.graphics.Color

// Peregrine Dark Color Palette
// Surfaces (Blended halfway between your grays and the image's deep plums)
val Surface = Color(0xFF200A27)           // Midpoint of 141218 and 1B1426
val SurfaceContainer = Color(0xFF2A1233)  // Midpoint of 211F26 and 291E37
val SurfaceContainerHigh = Color(0xFF2F273B) // Midpoint of 2B2930 and 342646

// Text and Icon Colors (Slightly tinted towards purple)
val OnSurface = Color(0xFFE2D9EA)         // Midpoint of E6E0E9 and DFD4E9
val OnSurfaceVariant = Color(0xFFC3BACA)  // Midpoint of CAC4D0 and BDB0CA

// Primary Accent Colors (The new 50% muted purple)
val Primary = Color(0xFFD0BCFF)           // Midpoint of D0BCFF and 4A4458 0xFF8D80AC
val OnPrimary = Color(0xFF341A6B)         // Midpoint of 381E72 and 301663

// Secondary Elements
val SecondaryContainer = Color(0xFF473D5A) // Midpoint of 4A4458 and 45365B
val OnSecondaryContainer = Color(0xFFE0D3F2) // Midpoint of E8DEF8 and D8C8EC

// Borders and Dividers
val Outline = Color(0xFF4C435A)           // Midpoint of 49454F and 504264
val OutlineVariant = Color(0xFF473F53)    // Midpoint of 49454F and 463958
// Peregrine Dark Color Palette
//val Surface = Color(0xFF141218)
//val SurfaceContainer = Color(0xFF211F26)
//val SurfaceContainerHigh = Color(0xFF2B2930)
//val OnSurface = Color(0xFFE6E0E9)
//val OnSurfaceVariant = Color(0xFFCAC4D0)
//val Primary = Color(0xFFD0BCFF)
//val OnPrimary = Color(0xFF381E72)
//val SecondaryContainer = Color(0xFF4A4458)
//val OnSecondaryContainer = Color(0xFFE8DEF8)
//val Outline = Color(0xFF49454F)
//val OutlineVariant = Color(0xFF49454F)

// Functional pops
val AlertBadgeBg = Color(0xFFFFB4AB)
val AlertBadgeText = Color(0xFF690005)
val LiveDot = Color(0xFFFE7D8B) //0xFFFF5449

// Typography Colours
val HeadingColor = Color(0xFFD0BCFF)

// Custom detection palette
object DetectionColors {
    data class Pair(val container: Color, val onContainer: Color, val bright: Color, val onBright: Color)
    val Person  = Pair(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFD0BCFF), Color(0xFF381E72))
    val Vehicle = Pair(Color(0xFF234B6E), Color(0xFFCFE5FF), Color(0xFFA6CBFF), Color(0xFF0A2A4D))
    val Animal  = Pair(Color(0xFF2E4B33), Color(0xFFBCEFBE), Color(0xFF9BE0A0), Color(0xFF0A3311))
    val Verified = Pair(Color(0xFF00513B), Color(0xFFB5F3D1), Color(0xFF34D399), Color(0xFF064E3B))
}
