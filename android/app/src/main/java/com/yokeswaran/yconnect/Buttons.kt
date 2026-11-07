// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
package com.yokeswaran.yconnect

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * Buttons with the Cat Senpai look: the Material 3 pill shape everywhere.
 * Ui.kt, RunUi.kt and LatencyUi.kt do not import the Material 3 versions, so these same-package functions are the
 * ones their calls resolve to. The `shape` parameter is kept so existing calls compile, but the theme shape wins.
 */

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = androidx.compose.material3.ButtonDefaults.shape,
    interactionSource = interactionSource, content = content
)

@Composable
internal fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.FilledTonalButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = androidx.compose.material3.ButtonDefaults.filledTonalShape,
    interactionSource = interactionSource, content = content
)

@Composable
internal fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = androidx.compose.material3.ButtonDefaults.outlinedShape,
    interactionSource = interactionSource, content = content
)
