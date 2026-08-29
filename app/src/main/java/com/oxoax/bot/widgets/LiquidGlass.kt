package com.oxoax.bot.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
fun BackdropProvider(content: @Composable () -> Unit) {
    val backdrop = rememberCanvasBackdrop { }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        content()
    }
}

@Composable
fun LiquidGlassBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    )
                } else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    )
                } else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCircle(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current

    Box(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    )
                } else Modifier
            )
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCapsule(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val shape = remember { RoundedCornerShape(50) }

    Box(
        modifier = modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        }
                    )
                } else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.15f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}
