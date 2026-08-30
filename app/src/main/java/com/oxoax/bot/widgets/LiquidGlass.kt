package com.oxoax.bot.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val LocalBackdrop = compositionLocalOf<LayerBackdrop> { error("No Backdrop provided") }

@Composable
fun BackdropProvider(content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        content()
    }
}

/**
 * 在背景图上使用此 Modifier，捕获背景内容供液态玻璃折射
 */
fun Modifier.captureBackdrop(backdrop: LayerBackdrop): Modifier {
    return this.layerBackdrop(backdrop)
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
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                }
            ),
        content = content
    )
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
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                }
            ),
        content = content
    )
}

@Composable
fun LiquidGlassCircle(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                }
            ),
        content = content
    )
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
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                }
            ),
        content = content
    )
}
