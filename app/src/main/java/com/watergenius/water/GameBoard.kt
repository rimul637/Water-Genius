package com.watergenius.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

internal class Round(val level: Level) {
    val rotations = level.initial.copyOf()
    val displayed = FloatArray(rotations.size) { rotations[it] * 90f }
    val wetAt = FloatArray(rotations.size)
    val bloom = FloatArray(rotations.size)
    var water by mutableStateOf(flow(level, rotations))
        private set
    var moves by mutableIntStateOf(0)
        private set
    var hints by mutableIntStateOf(0)
        private set
    var currentHint by mutableStateOf<Hint?>(null)
        private set
    private val target = displayed.copyOf()
    private var previousTime = -1f

    init {
        for (cell in rotations.indices) {
            wetAt[cell] = if (water.wet(cell)) -.4f else Float.POSITIVE_INFINITY
            bloom[cell] = if (water.wet(cell)) 1f else 0f
        }
    }

    fun rotate(cell: Int, time: Float): Boolean {
        val tile = level.tiles[cell]
        if (tile.fixed || water.complete) return false
        val old = water
        rotations[cell] = (rotations[cell] + 1) % tile.period
        moves++
        target[cell] += 90f
        water = flow(level, rotations)
        var bloomed = false
        for (index in rotations.indices) {
            if (water.wet(index) && !old.wet(index)) {
                wetAt[index] = time + min(water.distance[index], 14) * .029f
                if (level.tiles[index].piece == Piece.PLANT) bloomed = true
            } else if (!water.wet(index)) {
                wetAt[index] = Float.POSITIVE_INFINITY
            }
        }
        if (currentHint?.cell == cell) {
            val turns = tile.clockwiseToSolution(rotations[cell])
            currentHint = if (turns == 0) null else Hint(cell, turns)
        }
        return bloomed
    }

    fun showHint() {
        val next = hint(level, rotations, water) ?: return
        if (currentHint != next) hints++
        currentHint = next
    }

    fun advance(time: Float) {
        val delta = if (previousTime < 0f) 0f else (time - previousTime).coerceIn(0f, .045f)
        previousTime = time
        for (cell in rotations.indices) {
            displayed[cell] += (target[cell] - displayed[cell]) * min(1f, delta * 23f)
            val growing = time >= wetAt[cell]
            val change = if (growing) delta * 1.65f else -delta * 2.6f
            bloom[cell] = (bloom[cell] + change).coerceIn(0f, 1f)
        }
    }
}

@Composable internal fun GameBoard(
    round: Round,
    art: Artwork,
    clock: State<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onRotate: (Int) -> Unit
) {
    val level = round.level
    var cell by remember(round) { mutableIntStateOf(level.tiles.indexOfFirst { !it.fixed }) }
    var focus by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(round, enabled) { if (enabled) requester.requestFocus() }

    Canvas(modifier.clipToBounds()
        .focusRequester(requester)
        .onFocusChanged { focus = it.isFocused }
        .onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val column = cell % level.width
            val row = cell / level.width
            when (event.key) {
                Key.DirectionUp -> if (row > 0) cell -= level.width
                Key.DirectionDown -> if (row < level.height - 1) cell += level.width
                Key.DirectionLeft -> if (column > 0) cell--
                Key.DirectionRight -> if (column < level.width - 1) cell++
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> onRotate(cell)
                else -> return@onPreviewKeyEvent false
            }
            true
        }
        .focusable(enabled)
        .semantics { contentDescription = "Water network. Use arrow keys to select a tile and Enter to rotate it." }
        .pointerInput(round, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { tap ->
                val tile = min(size.width.toFloat() / level.width, size.height.toFloat() / level.height)
                val column = floor((tap.x - (size.width - tile * level.width) / 2) / tile).toInt()
                val row = floor((tap.y - (size.height - tile * level.height) / 2) / tile).toInt()
                if (column in 0 until level.width && row in 0 until level.height) {
                    cell = row * level.width + column
                    onRotate(cell)
                }
            }
        }
    ) {
        val time = clock.value
        round.advance(time)
        val edge = min(size.width / level.width, size.height / level.height)
        val origin = Offset((size.width - edge * level.width) / 2, (size.height - edge * level.height) / 2)
        val boardSize = Size(edge * level.width, edge * level.height)
        drawRoundRect(Color(0xBC052C31), origin, boardSize, CornerRadius(edge * .22f))
        drawRoundRect(Color(0xCBDBAE58), origin, boardSize, CornerRadius(edge * .22f), style = Stroke(edge * .036f))
        for (index in level.tiles.indices) {
            val corner = origin + Offset(index % level.width * edge, index / level.width * edge)
            val center = corner + Offset(edge / 2, edge / 2)
            val geometry = Size(edge, edge)
            val tile = level.tiles[index]
            if (tile.piece != Piece.BLOCK) {
                drawRoundRect(Color(0x503E726C), corner + Offset(edge * .035f, edge * .035f),
                    Size(edge * .93f, edge * .93f), CornerRadius(edge * .13f))
            }
            val wet = time >= round.wetAt[index]
            val mask = round.water.masks[index]
            when (tile.piece) {
                Piece.BLOCK -> sprite(art.sprite("stone"), center, geometry * .72f, .64f)
                Piece.PLANT, Piece.SOURCE -> {
                    port(mask, center, edge, wet)
                    if (tile.piece == Piece.SOURCE) {
                        sprite(art.sprite("tank"), center, geometry * .80f)
                        drawCircle(Color(0xAEE0FFFF), edge * .026f,
                            center + Offset(edge * .069f, edge * (.18f - ((time * .44f) % .22f))))
                    } else plant(art, level.chapter, center, edge, round.bloom[index], time)
                }
                else -> {
                    val shape = when (tile.piece) {
                        Piece.ELBOW -> "elbow"
                        Piece.STRAIGHT -> "straight"
                        Piece.TEE -> "tee"
                        Piece.CROSS -> "cross"
                        else -> if (round.rotations[index] % 2 == 0) "valve_open" else "valve_closed"
                    }
                    val degrees = baseDegrees(tile) + round.displayed[index]
                    rotate(degrees, center) {
                        val image = art.sprite(shape + if (wet) "_wet" else "_dry")
                        sprite(image, center, geometry)
                    }
                    if (tile.fixed) {
                        sprite(art.sprite("lock"), corner + Offset(edge * .82f, edge * .18f), geometry * .23f)
                    }
                    if (wet) bubbles(mask, center, edge, time, index)
                }
            }
            if (wet && round.water.leaks[index] != 0) leaks(round.water.leaks[index], center, edge, time)
            val suggested = round.currentHint?.cell == index
            if (suggested || (focus && cell == index)) {
                val inset = edge * .04f
                val color = if (suggested) Color(0xFFFFDA65).copy(alpha = .75f + .25f * sin(time * 7f))
                    else Color(0xFF9DF9FF)
                drawRoundRect(color, corner + Offset(inset, inset), Size(edge - inset * 2, edge - inset * 2),
                    CornerRadius(edge * .13f), style = Stroke(edge * .035f))
                if (suggested) {
                    val arrow = center + Offset(edge * .24f, -edge * .24f)
                    drawArc(color, -150f, 235f, false, arrow - Offset(edge * .12f, edge * .12f),
                        Size(edge * .24f, edge * .24f), style = Stroke(edge * .028f))
                    drawLine(color, arrow + Offset(edge * .01f, edge * .12f),
                        arrow + Offset(edge * .065f, edge * .19f), edge * .027f)
                }
            }
        }
    }
}

private fun baseDegrees(tile: Tile): Float {
    var opening = when (tile.piece) {
        Piece.ELBOW -> 3
        Piece.TEE -> 7
        Piece.CROSS -> 15
        else -> 5
    }
    for (step in 0..3) {
        if (opening == tile.solution) return step * 90f
        opening = turn(opening)
    }
    return 0f
}

private fun vector(direction: Int): Offset = when (direction) {
    0 -> Offset(0f, -1f)
    1 -> Offset(1f, 0f)
    2 -> Offset(0f, 1f)
    else -> Offset(-1f, 0f)
}

private fun DrawScope.port(mask: Int, center: Offset, edge: Float, wet: Boolean) {
    for (direction in directions.indices) if (mask and directions[direction] != 0) {
        val end = center + vector(direction) * edge * .50f
        drawLine(Color(0xFFB58647), center, end, edge * .29f)
        drawLine(Color(0xFF455864), center, end, edge * .21f)
        drawLine(if (wet) Color(0xFF2EE0FC) else Color(0xFF193B50), center, end, edge * .13f)
    }
}

private fun DrawScope.bubbles(mask: Int, center: Offset, edge: Float, time: Float, index: Int) {
    for (direction in directions.indices) if (mask and directions[direction] != 0) {
        val phase = (time * 1.24f + direction * .12f + index * .19f) % 1f
        val stretch = vector(direction)
        val head = center + stretch * (edge * (.23f + phase * .24f))
        drawLine(Color(0xCDA7F8FF), head, head - stretch * (edge * .065f), edge * .027f)
        drawCircle(Color(0xDDF6FFFF), edge * .017f, head)
    }
}

private fun DrawScope.leaks(mask: Int, center: Offset, edge: Float, time: Float) {
    for (direction in directions.indices) if (mask and directions[direction] != 0) {
        val vector = vector(direction)
        val outlet = center + vector * edge * .42f
        drawCircle(Color(0xFFEABC65), edge * .074f, outlet, style = Stroke(edge * .024f))
        for (i in 0..2) {
            val phase = (time * 2.1f + i * .32f) % 1f
            val side = Offset(-vector.y, vector.x)
            val drop = outlet + vector * (phase * edge * .20f) + side * ((i - 1) * phase * edge * .15f)
            drawCircle(Color(0xFF78EEFF).copy(alpha = 1 - phase), edge * (.031f - .014f * phase), drop)
        }
    }
}

private fun DrawScope.plant(art: Artwork, chapter: Int, center: Offset, edge: Float, growth: Float, time: Float) {
    val dimensions = Size(edge * .84f, edge * .84f)
    sprite(art.sprite("wilt"), center, dimensions, 1f - min(1f, growth * 2f))
    val tall = .60f + growth * .44f
    sprite(art.sprite("seedling"), center + Offset(0f, dimensions.height * (1f-tall) / 2),
        Size(dimensions.width * tall, dimensions.height * tall), sin(PI.toFloat() * growth).coerceAtLeast(0f))
    val grown = ((growth - .33f) / .67f).coerceIn(0f, 1f)
    sprite(art.sprite("plant_$chapter"), center + Offset(0f, dimensions.height * (1f-tall) / 2),
        Size(dimensions.width * tall, dimensions.height * tall), grown)
    if (growth in .11f.. .98f) {
        val halo = edge * .46f
        drawCircle(Brush.radialGradient(listOf(Color(0x3EFFFBA1), Color.Transparent), center, halo), halo, center)
        for (i in 0..4) {
            val angle = i * 1.26f + time * .8f
            val spark = center + Offset(sin(angle), sin(angle + 1.57f)) * (edge * (.22f + growth * .22f))
            drawCircle(Color(0xFFFFEF9D), edge * .027f * (1f - growth), spark)
        }
    }
}
