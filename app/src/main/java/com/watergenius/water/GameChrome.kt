package com.watergenius.water

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

internal val CompactChrome = staticCompositionLocalOf { false }
internal val LocalGameClock = staticCompositionLocalOf<State<Float>?> { null }

@Composable internal fun GameButton(
    art: Artwork, label: String, modifier: Modifier = Modifier, blue: Boolean = false,
    small: Boolean = false, enabled: Boolean = true, onClick: () -> Unit
) {
    val compact = small || CompactChrome.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (pressed) .92f else 1f,
        spring(dampingRatio = .53f, stiffness = 490f), label = "button press")
    val image = art.sprite(if (blue) "button_blue" else "button_green")
    val clock = LocalGameClock.current
    val sheenOffset = remember(label) { (label.hashCode().toUInt() % 120u).toFloat() / 100f }
    Box(
        modifier.widthIn(max = if (compact) 152.dp else 246.dp).fillMaxWidth()
            .height(if (compact) 48.dp else 72.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationY = if (pressed) 1.5.dp.toPx() else 0f
                    alpha = if (enabled) 1f else .53f
                }
                .drawWithCache {
                    val ratio = image.width.toFloat() / image.height
                    val width = min(size.width, size.height * ratio)
                    val height = width / ratio
                    val x = (size.width - width) * .5f
                    val y = (size.height - height) * .5f
                    val face = Path().apply {
                        addRoundRect(RoundRect(x + width * .055f, y + height * .11f,
                            x + width * .945f, y + height * .78f, CornerRadius(height * .25f)))
                    }
                    val sweep = width * .34f
                    val reflection = Brush.linearGradient(
                        0f to Color.Transparent, .42f to Color(0x15FFFFFF),
                        .55f to Color(0x52FFFBD8), .63f to Color(0x15FFFFFF), 1f to Color.Transparent,
                        end = Offset(sweep, -height * .47f))
                    val focusWidth = 2.dp.toPx()
                    val focusRing = Stroke(focusWidth)
                    onDrawBehind {
                        sprite(image, center, Size(width, height))
                        if (enabled && clock != null) {
                            val phase = (clock.value + sheenOffset) % 4.6f
                            if (phase < 1.22f) {
                                val position = x - sweep + (width + sweep * 1.4f) * (phase / 1.22f)
                                clipPath(face) {
                                    translate(position, y) { drawRect(reflection, size = Size(sweep, height)) }
                                }
                            }
                        }
                        if (focused) {
                            drawRoundRect(Color(0xFFBDFFF2), Offset(x + focusWidth, y + focusWidth),
                                Size((width - focusWidth * 2).coerceAtLeast(0f), (height - focusWidth * 2).coerceAtLeast(0f)),
                                CornerRadius(height * .36f), style = focusRing)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            TextLine(label.uppercase(java.util.Locale.ROOT), if (compact) 12 else 16,
                Color(0xFFFFF6D2), FontWeight.Black, Modifier.offset(y = (-2).dp).padding(horizontal = 5.dp))
        }
    }
}

@Composable internal fun SettingsToggle(
    label: String, checked: Boolean, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    val position = animateFloatAsState(if (checked) 1f else 0f,
        spring(dampingRatio = .68f, stiffness = 510f), label = "toggle position")
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().widthIn(max = 254.dp).height(49.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .clickable(indication = null, interactionSource = null, enabled = enabled, role = Role.Switch, onClick = onClick)
            .drawWithCache {
                val inset = 1.dp.toPx()
                val rounding = CornerRadius(14.dp.toPx())
                val outline = Stroke(inset)
                onDrawBehind {
                    drawRoundRect(Color(0x55152B26), cornerRadius = rounding)
                    if (focused) drawRoundRect(Color(0xFFBDFFF2), Offset(inset, inset),
                        Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)), rounding, style = outline)
                }
            }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextLine(label, 14, Color(0xFFFFF1C6), FontWeight.Bold, Modifier.weight(1f))
        Canvas(Modifier.size(52.dp, 28.dp)) {
            val amount = position.value.coerceIn(0f, 1f)
            val radius = size.height * .5f
            drawRoundRect(Color(0xFFCEAA65), cornerRadius = CornerRadius(radius))
            drawRoundRect(androidx.compose.ui.graphics.lerp(Color(0xFF1F3136), Color(0xFF168958), amount),
                Offset(1.6.dp.toPx(), 1.6.dp.toPx()), Size(size.width - 3.2.dp.toPx(), size.height - 3.2.dp.toPx()),
                CornerRadius(radius))
            val dot = Offset(radius + (size.width - 2 * radius) * amount, radius)
            drawCircle(Color(0xFFA46B28), radius * .75f, dot + Offset(0f, 1.2.dp.toPx()))
            drawCircle(Color(0xFFFFD981), radius * .70f, dot)
            drawCircle(Color(0xFFFFF1BC), radius * .35f, dot - Offset(radius * .09f, radius * .17f))
        }
    }
}

internal fun Modifier.enamelFrame(image: ImageBitmap, cornerDp: Int = 26) = drawWithCache {
    val width = size.width.roundToInt()
    val height = size.height.roundToInt()
    val corner = min(cornerDp.dp.roundToPx(), min(width, height) / 2)
    val sourceCorner = min(image.width, image.height) / 4
    val sx = intArrayOf(0, sourceCorner, image.width - sourceCorner, image.width)
    val sy = intArrayOf(0, sourceCorner, image.height - sourceCorner, image.height)
    val dx = intArrayOf(0, corner, width - corner, width)
    val dy = intArrayOf(0, corner, height - corner, height)
    onDrawBehind {
        for (row in 0..2) for (column in 0..2) {
            val sw = sx[column + 1] - sx[column]
            val sh = sy[row + 1] - sy[row]
            val dw = dx[column + 1] - dx[column]
            val dh = dy[row + 1] - dy[row]
            if (dw > 0 && dh > 0) {
                drawImage(image, srcOffset = IntOffset(sx[column], sy[row]), srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(dx[column], dy[row]), dstSize = IntSize(dw, dh))
            }
        }
    }
}
