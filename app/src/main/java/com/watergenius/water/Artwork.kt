package com.watergenius.water

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal class Artwork(private val context: Context) {
    private val loaded = mutableMapOf<String, ImageBitmap>()
    private val environments = listOf("garden", "forest", "desert", "frozen", "tropical")
    private val common = listOf(
        "button_green", "button_blue", "panel_green", "star", "seedling", "wilt", "tank", "stone",
        "butterfly", "lock", "logo", "flowers_foreground"
    )

    fun loadCommon() {
        common.forEach(::sprite)
        for (piece in listOf("straight", "elbow", "tee", "cross", "valve_open", "valve_closed")) {
            sprite("${piece}_wet")
            sprite("${piece}_dry")
        }
        for (chapter in 0..4) sprite("plant_$chapter")
    }

    fun scene(chapter: Int) = asset(environments[chapter], "webp")

    fun sprite(name: String) = asset(name, "png")

    @Synchronized private fun asset(name: String, extension: String): ImageBitmap = loaded.getOrPut(name) {
        context.assets.open("$name.$extension").use {
            checkNotNull(BitmapFactory.decodeStream(it)) { "Cannot decode $name" }.asImageBitmap()
        }
    }
}

@Composable internal fun frameClock(active: Boolean = true): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            clock.floatValue += ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
            previous = now
        }
    }
    return clock
}

internal fun DrawScope.sprite(image: ImageBitmap, center: Offset, dimensions: Size, opacity: Float = 1f) {
    if (dimensions.minDimension < 1 || opacity <= 0) return
    val scale = minOf(dimensions.width / image.width, dimensions.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(
            (center.x - width / 2).roundToInt(),
            (center.y - height / 2).roundToInt()
        ),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        alpha = opacity.coerceAtMost(1f)
    )
}
