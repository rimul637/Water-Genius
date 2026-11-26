package com.watergenius.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val trail = floatArrayOf(.24f, .48f, .73f, .82f, .65f, .38f, .20f, .33f, .59f, .76f)

@Composable internal fun AdventureMap(
    art: Artwork, progress: Progress, clock: State<Float>, enabled: Boolean,
    onHome: () -> Unit, onLevel: (Int) -> Unit
) {
    val home = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) home.requestFocus() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        Row(Modifier.fillMaxWidth().background(Color(0xE8053038)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            GameButton(art, "Home", Modifier.width(95.dp).focusRequester(home),
                blue = true, small = true, enabled = enabled, onClick = onHome)
            TextLine("Adventure Map", 17, Color(0xFFFFEDAD), FontWeight.ExtraBold, Modifier.weight(1f).padding(horizontal = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Image(art.sprite("star"), null, Modifier.size(19.dp))
                Caption("${progress.total}/150")
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (chapter in 0..4) {
                ChapterMap(art, chapter, progress, clock, enabled, onLevel)
            }
        }
    }
}

@Composable private fun ChapterMap(
    art: Artwork, chapter: Int, progress: Progress, clock: State<Float>, enabled: Boolean, onLevel: (Int) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(720.dp)) {
        Image(art.scene(chapter), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0xB6042C32), Color.Transparent, Color(0xAD042C32)
        ))))
        Box(Modifier.matchParentSize().drawWithCache {
            val path = Path()
            val firstX = size.width * trail.first()
            path.moveTo(firstX, size.height * .065f)
            for (i in trail.indices) {
                val y = size.height * (.14f + i * .085f)
                val x = size.width * trail[i]
                if (i == 0) path.lineTo(x, y) else {
                    val priorX = size.width * trail[i - 1]
                    val priorY = y - size.height * .085f
                    val mid = (priorY + y) / 2
                    path.cubicTo(priorX, mid, x, mid, x, y)
                }
            }
            val border = Stroke(19.dp.toPx(), cap = StrokeCap.Round)
            val track = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            val inset = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            onDrawBehind {
                drawPath(path, Color(0xCD3F2C27), style = border)
                drawPath(path, Color(0xE4C28C50), style = track)
                drawPath(path, Color(0xBDFFE8AA), style = inset)
            }
        })
        Box(Modifier.align(Alignment.TopCenter).padding(top = 13.dp)
            .enamelFrame(art.sprite("panel_green"), 17).padding(horizontal = 18.dp, vertical = 8.dp)) {
            TextLine(chapterNames[chapter], 23, Color(0xFFFFEDAD), FontWeight.ExtraBold)
        }
        for (stage in 0..9) {
            val level = chapter * 10 + stage + 1
            val unlocked = progress.unlocked(level) && enabled
            val stars = progress.best[level] ?: 0
            val alive = level == progress.next && stars == 0
            Column(
                Modifier.offset(x = maxWidth * trail[stage] - 30.dp, y = maxHeight * (.14f + stage * .085f) - 24.dp)
                    .width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var focus by remember { mutableStateOf(false) }
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val bounce = animateFloatAsState(if (pressed) .91f else 1f,
                    spring(dampingRatio = .57f, stiffness = 515f), label = "map medallion")
                val open = progress.unlocked(level)
                Box(Modifier.size(50.dp).onFocusChanged { focus = it.isFocused }
                    .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                    .drawWithCache {
                        val outer = Brush.verticalGradient(listOf(Color(0xFFFFE7A0), Color(0xFFBA7836), Color(0xFFFFCD70)))
                        val enamel = Brush.radialGradient(
                            if (open) listOf(Color(0xFF649C5A), Color(0xFF226855), Color(0xFF102C39))
                            else listOf(Color(0xFF5B7770), Color(0xFF253F45), Color(0xFF111F2A)),
                            center = Offset(size.width * .37f, size.height * .32f), radius = size.width * .65f
                        )
                        onDrawBehind {
                            val radius = size.minDimension * .45f
                            drawCircle(Color(0xAC171916), radius, center + Offset(0f, 2.dp.toPx()))
                            drawCircle(outer, radius, center - Offset(0f, 1.dp.toPx()))
                            drawCircle(enamel, radius * .83f, center - Offset(0f, 1.dp.toPx()))
                            if (focus) drawCircle(Color(0xFFB5FAFF), radius * 1.07f,
                                style = Stroke(2.dp.toPx()))
                        }
                    }
                    .semantics { contentDescription = if (open) "Level $level, $stars stars" else "Level $level, locked" }
                    .clickable(interactions, indication = null, enabled = unlocked, role = Role.Button) { onLevel(level) },
                    contentAlignment = Alignment.Center) {
                    if (alive) Canvas(Modifier.matchParentSize()) {
                        val pulse = .5f + .5f * sin(clock.value * 5.2f)
                        drawCircle(Color(0xFFC8FFE6).copy(alpha = .24f + .47f * pulse),
                            size.width * (.38f + .058f * pulse), style = Stroke(2.dp.toPx()))
                    }
                    if (progress.unlocked(level)) {
                        TextLine(level.toString(), 19, Color(0xFFFFF2C1), FontWeight.Black)
                    } else Image(art.sprite("lock"), null, Modifier.size(24.dp))
                }
                if (stars > 0) Stars(art, stars, 12)
            }
        }
    }
}
