// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
package com.yokeswaran.yconnect

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Look and motion shared with Cat Senpai: blurple Material 3 palette, big rounded tonal cards, springy movement.

// Y Connect's default blue (the "Miku blue" of the desktop app) replaces Cat Senpai's blurple; the neutral greys stay.
internal val MikuBlue = Color(0xFF5AA5F0)
internal val MikuBlueDeep = Color(0xFF2563C4)
internal val StatusOnline = Color(0xFF23A55A)
internal val StatusIdle = Color(0xFFF0B232)

internal val CatDark = darkColorScheme(
    primary = MikuBlue, onPrimary = Color(0xFF04121F), primaryContainer = Color(0xFF1F4E82), onPrimaryContainer = Color(0xFFD3E5FF),
    secondary = Color(0xFF9FB4D6), tertiary = Color(0xFF3BC47A), onTertiary = Color(0xFF00210F),
    background = Color(0xFF1E1F22), onBackground = Color(0xFFF2F3F5), surface = Color(0xFF1E1F22), onSurface = Color(0xFFF2F3F5),
    surfaceContainerLowest = Color(0xFF111214), surfaceContainerLow = Color(0xFF232428), surfaceContainer = Color(0xFF2B2D31),
    surfaceContainerHigh = Color(0xFF33353B), surfaceContainerHighest = Color(0xFF3C3E45),
    surfaceVariant = Color(0xFF383A40), onSurfaceVariant = Color(0xFFB5BAC1), outline = Color(0xFF80848E), outlineVariant = Color(0xFF3F4147),
    secondaryContainer = Color(0xFF28425F), onSecondaryContainer = Color(0xFFD3E5FF), error = Color(0xFFF87171)
)

internal val CatLight = lightColorScheme(
    primary = MikuBlueDeep, onPrimary = Color.White, primaryContainer = Color(0xFFD3E5FF), onPrimaryContainer = Color(0xFF0B1F36),
    secondary = Color(0xFF52627E), tertiary = Color(0xFF1A7F45), onTertiary = Color.White,
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF060607), surface = Color(0xFFF7F8FA), onSurface = Color(0xFF060607),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F5), surfaceContainer = Color(0xFFEBEDEF),
    surfaceContainerHigh = Color(0xFFE3E5E8), surfaceContainerHighest = Color(0xFFDADCE0),
    surfaceVariant = Color(0xFFEBEDEF), onSurfaceVariant = Color(0xFF4E5058), outline = Color(0xFF80848E), outlineVariant = Color(0xFFD4D7DC),
    secondaryContainer = Color(0xFFD8E6FB), onSecondaryContainer = Color(0xFF0B1F36), error = Color(0xFFC62828)
)

internal val CatShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)
)

internal fun catTypography(): Typography {
    val b = Typography()
    return b.copy(
        headlineMedium = b.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = b.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = b.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = b.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

/** Shared animation specs, so every screen moves the same way. */
internal object Motion {
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
}
