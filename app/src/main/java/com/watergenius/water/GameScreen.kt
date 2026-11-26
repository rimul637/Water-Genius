package com.watergenius.water

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

private enum class Page { Splash, Home, Map, Play }
private enum class Popup { Settings, Pause, Win }
private val cream = Color(0xFFFFEFBB)
private val mint = Color(0xFFD8FFF0)

@Composable internal fun WaterGenius(store: ProgressStore, audio: GameAudio) {
    val context = LocalContext.current
    val art = remember { Artwork(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val progress by store.progress.collectAsState(initial = null)
    val saved = progress ?: Progress()
    var page by remember { mutableStateOf(Page.Splash) }
    var popup by remember { mutableStateOf<Popup?>(null) }
    var round by remember { mutableStateOf<Round?>(null) }
    var scene by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var result by remember { mutableIntStateOf(0) }
    val current = round
    val chapter = if (page == Page.Play) current?.level?.chapter ?: 0 else 0
    val active = popup == null || popup == Popup.Win
    val clock = frameClock()
    val pauseTime = remember(active) { if (active) null else mutableFloatStateOf(clock.value) }
    val gameplayClock: State<Float> = pauseTime ?: clock
    val home: () -> Unit = {
        result = 0
        popup = null
        round = null
        page = Page.Home
    }
    fun start(number: Int) {
        round = Round(Levels.all[number - 1])
        result = 0
        popup = null
        page = Page.Play
    }
    fun back() {
        when (popup) {
            Popup.Pause, Popup.Settings -> popup = null
            Popup.Win -> if (result != 0) home()
            null -> when (page) {
                Page.Map -> home()
                Page.Play -> if (current?.water?.complete == false) popup = Popup.Pause
                else -> Unit
            }
        }
    }
    BackHandler(page != Page.Home || popup != null, ::back)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            art.loadCommon()
            for (environment in 0..4) art.scene(environment)
            Levels.all.size
        }
        ready = true
    }
    LaunchedEffect(ready, progress != null) {
        if (ready && progress != null && page == Page.Splash) page = Page.Home
    }
    LaunchedEffect(chapter) {
        scene = withContext(Dispatchers.IO) { art.scene(chapter) }
    }
    val watered = current?.water?.distance?.any { it > 0 } == true
    LaunchedEffect(page, popup, saved.music, saved.effects, watered) {
        val track = when {
            page == Page.Splash || popup == Popup.Pause -> null
            page == Page.Play -> "game_music"
            else -> "menu_music"
        }
        audio.configure(track, saved.music, saved.effects, page == Page.Play && popup == null && watered)
    }
    fun rotate(cell: Int) {
        val game = current ?: return
        if (popup != null || game.water.complete || game.level.tiles[cell].fixed) return
        audio.play("rotate")
        if (game.rotate(cell, clock.value)) audio.play("bloom")
        if (game.water.complete) {
            result = stars(game.level.par, game.moves, game.hints)
            scope.launch {
                store.complete(game.level.number, result)
                delay(760)
                audio.play("star_sound")
                audio.play("win")
                popup = Popup.Win
            }
        }
    }
    val enabled = popup == null && result == 0
    CompositionLocalProvider(LocalGameClock provides clock) {
        Box(Modifier.fillMaxSize().background(Color(0xFF093C3C)).onPreviewKeyEvent { event ->
            if (event.key != Key.Escape || (page == Page.Home && popup == null)) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) back()
            true
        }) {
            val background = scene
            if (background != null) {
                Image(background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (ready && page == Page.Home) Ambient(art, clock)
            }
            AnimatedContent(page, Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(220, delayMillis = 45)) togetherWith fadeOut(tween(150)) },
                label = "screen") { shown ->
                val screenEnabled = enabled && shown == page
                when (shown) {
                    Page.Splash -> Splash(clock)
                    Page.Home -> HomeScreen(art, screenEnabled,
                        onPlay = { audio.play("click"); start(saved.next) },
                        onLevels = { audio.play("click"); page = Page.Map },
                        onSettings = { audio.play("click"); popup = Popup.Settings })
                    Page.Map -> AdventureMap(art, saved, clock, screenEnabled,
                        onHome = { audio.play("click"); home() },
                        onLevel = { audio.play("click"); start(it) })
                    Page.Play -> if (current != null) {
                        PlayScreen(art, current, gameplayClock, screenEnabled, ::rotate,
                            onPause = { audio.play("click"); popup = Popup.Pause },
                            onRestart = { audio.play("click"); start(current.level.number) },
                            onHint = { audio.play("hint"); current.showHint() })
                    }
                }
            }
            AnimatedContent(popup, Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(145))
                }, label = "dialog") { modal ->
                if (modal != null) {
                    val first = remember(modal) { FocusRequester() }
                    val winRound = remember(modal) { current }
                    val winStars = remember(modal) { result }
                    LaunchedEffect(modal, popup) { if (modal == popup && (modal != Popup.Win || current != null)) first.requestFocus() }
                    GamePopup(art, modal == Popup.Win, clock) {
                        when (modal) {
                            Popup.Settings -> {
                                Title("Settings")
                                SettingsToggle("Music", saved.music, Modifier.focusRequester(first), modal == popup) {
                                    audio.play("click")
                                    scope.launch { store.toggleMusic() }
                                }
                                SettingsToggle("Sound effects", saved.effects, enabled = modal == popup) {
                                    audio.play("click")
                                    scope.launch { store.toggleEffects() }
                                }
                                GameButton(art, "Close", blue = true, enabled = modal == popup) { audio.play("click"); popup = null }
                            }
                            Popup.Pause -> {
                                Title("Paused")
                                GameButton(art, "Resume", Modifier.focusRequester(first), enabled = modal == popup) { audio.play("click"); popup = null }
                                GameButton(art, "Restart", blue = true, enabled = modal == popup) { audio.play("click"); current?.let { start(it.level.number) } }
                                GameButton(art, "Home", blue = true, enabled = modal == popup) { audio.play("click"); home() }
                            }
                            Popup.Win -> if (winRound != null) {
                                val last = winRound.level.number == 50
                                Title(if (last) "Garden restored!" else "Level complete")
                                Stars(art, winStars, 39, clock)
                                Caption(if (last) "50 levels completed • ${saved.total} / 150 stars"
                                    else "${winRound.moves} moves • ${winRound.hints} hints")
                                GameButton(art, if (last) "Replay levels" else "Next level", Modifier.focusRequester(first), enabled = modal == popup) {
                                    audio.play("click")
                                    if (last) { popup = null; page = Page.Map; round = null; result = 0 }
                                    else start(winRound.level.number + 1)
                                }
                                if (!last) GameButton(art, "Replay", blue = true, enabled = modal == popup) { audio.play("click"); start(winRound.level.number) }
                                GameButton(art, "Home", blue = true, enabled = modal == popup) { audio.play("click"); home() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Splash(clock: State<Float>) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF178B86), Color(0xFF052E3A)))),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val time = clock.value
            for (i in 0..3) {
                val phase = (time * .72f + i * .25f) % 1f
                drawCircle(Color(0xFF92FFFF).copy(alpha = .32f * (1 - phase)), size.minDimension * (.16f + .33f * phase),
                    style = Stroke(2.dp.toPx()))
                val offset = Offset(cos(i * 1.7f), sin(i * 1.7f)) * (size.minDimension * (.25f + .20f * phase))
                drawCircle(Color(0xFFAAFEF0).copy(alpha = 1 - phase), 4.dp.toPx(), center + offset)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.splash_art), "Water Genius launcher artwork",
                Modifier.size(148.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)))
            Title("Water Genius")
        }
    }
}

@Composable private fun Ambient(art: Artwork, clock: State<Float>) {
    val butterfly = art.sprite("butterfly")
    val flower = art.sprite("flowers_foreground")
    val garden = art.scene(0)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (i in 0..4) {
            val position = Offset(size.width * (.5f + .40f * sin(t * (.17f + i * .028f) + i * 2.3f)),
                size.height * (.22f + .13f * cos(t * .37f + i * 2.2f)))
            val span = (18 + i * 3).dp.toPx()
            val wing = .46f + .54f * kotlin.math.abs(sin(t * 13f + i))
            rotate(12f * sin(t * 1.1f + i), position) {
                sprite(butterfly, position, Size(span * wing, span))
            }
        }
        val cover = maxOf(size.width / garden.width, size.height / garden.height)
        val sceneWidth = cover * garden.width
        val sceneHeight = cover * garden.height
        val sceneStart = Offset((size.width - sceneWidth) * .5f, (size.height - sceneHeight) * .5f)
        val falls = sceneStart + Offset(sceneWidth * .84f, sceneHeight * .21f)
        for (i in 0..24) {
            val phase = (i * .047f + t * (.50f + (i % 3) * .17f)) % 1f
            val source = falls + Offset(sceneWidth * ((i * 7) % 31 - 15) / 277f, sceneHeight * phase * .125f)
            val opacity = .22f * sin(phase * 3.14159f).coerceAtLeast(0f)
            drawLine(Color(0xFFD7FFF8).copy(alpha = opacity), source,
                source + Offset(-3.dp.toPx(), (8 + i % 13).dp.toPx()), (1 + i % 2).dp.toPx())
        }
        for (i in 0..13) {
            val phase = (t * .21f + i * .13f) % 1f
            val origin = Offset(size.width * ((i * 7 % 17) / 17f + .022f * sin(phase * 5f)),
                size.height * (.82f - .48f * phase))
            drawCircle(Color(0xFFD5FFF6).copy(alpha = .60f * sin(phase * 3.14f)),
                (1.1f + (i % 3) * .7f).dp.toPx(), origin)
        }
        for (i in 0..1) {
            val anchor = Offset(size.width * (if (i == 0) .09f else .91f), size.height * .97f)
            val height = (if (i == 0) 96 else 118).dp.toPx()
            rotate(5f * sin(t * 1.14f + i * 1.4f), anchor) {
                sprite(flower, anchor - Offset(0f, height * .5f), Size(height, height), .92f)
            }
        }
    }
}

@Composable private fun HomeScreen(art: Artwork, enabled: Boolean, onPlay: () -> Unit, onLevels: () -> Unit, onSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance = animateFloatAsState(if (entered) 1f else 0f,
        spring(dampingRatio = .69f, stiffness = 180f), label = "logo arrival")
    val clock = LocalGameClock.current
    val logoMotion = Modifier.graphicsLayer {
        val appear = entrance.value
        val size = .85f + .15f * appear
        scaleX = size
        scaleY = size
        alpha = appear.coerceIn(0f, 1f)
        translationY = -22.dp.toPx() * (1 - appear) + 2.5.dp.toPx() * sin((clock?.value ?: 0f) * 1.27f)
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        val wide = maxWidth > maxHeight * 1.10f
        val short = maxHeight < 410.dp
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent, .40f to Color(0x16042124), 1f to Color(0xBF052D29))))
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
                Image(art.sprite("logo"), "Water Genius",
                    Modifier.weight(1f).heightIn(max = 250.dp).then(logoMotion), contentScale = ContentScale.Fit)
                Column(Modifier.weight(.84f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton(art, "Play", Modifier.focusRequester(focus), small = short, enabled = enabled, onClick = onPlay)
                    GameButton(art, "Levels", blue = true, small = short, enabled = enabled, onClick = onLevels)
                    GameButton(art, "Settings", blue = true, small = short, enabled = enabled, onClick = onSettings)
                }
            }
        } else {
            val logoHeight = (maxHeight * .26f).coerceIn(76.dp, 226.dp)
            Column(Modifier.align(Alignment.Center).widthIn(max = 510.dp).fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (short) 7.dp else 10.dp)) {
                Image(art.sprite("logo"), "Water Genius",
                    Modifier.fillMaxWidth(.94f).height(logoHeight).then(logoMotion), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(if (short) 4.dp else 13.dp))
                GameButton(art, "Play", Modifier.focusRequester(focus), small = short, enabled = enabled, onClick = onPlay)
                GameButton(art, "Levels", blue = true, small = short, enabled = enabled, onClick = onLevels)
                GameButton(art, "Settings", blue = true, small = short, enabled = enabled, onClick = onSettings)
            }
        }
    }
}

@Composable private fun PlayScreen(
    art: Artwork, round: Round, clock: State<Float>, enabled: Boolean, onRotate: (Int) -> Unit,
    onPause: () -> Unit, onRestart: () -> Unit, onHint: () -> Unit
) {
    val level = round.level
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(10.dp)) {
        val landscape = maxWidth > maxHeight * 1.12f
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameBoard(round, art, clock, enabled, Modifier.weight(1f).fillMaxHeight(.99f), onRotate)
                Column(Modifier.widthIn(max = 200.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                    GameStatus(art, round, false)
                    round.currentHint?.let { Caption("${it.turns} clockwise ${if (it.turns == 1) "turn" else "turns"}") }
                    GameButton(art, "Pause", blue = true, small = true, enabled = enabled, onClick = onPause)
                    GameButton(art, "Restart", blue = true, small = true, enabled = enabled, onClick = onRestart)
                    GameButton(art, "Hint", small = true, enabled = enabled, onClick = onHint)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GameStatus(art, round, true)
                    GameButton(art, "Pause", Modifier.width(105.dp), blue = true, small = true, enabled = enabled, onClick = onPause)
                }
                GameBoard(round, art, clock, enabled, Modifier.weight(1f).fillMaxWidth(), onRotate)
                if (level.number == 1) Caption("Tap a pipe to turn it clockwise. Water the flower and seal every leak.",
                    Modifier.background(Color(0xBB052C35), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(8.dp))
                round.currentHint?.let { Caption("Highlighted pipe • ${it.turns} clockwise ${if (it.turns == 1) "turn" else "turns"}",
                    Modifier.background(Color(0xCC052C35), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(6.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                    GameButton(art, "Restart", Modifier.weight(1f, fill = false), blue = true, small = true, enabled = enabled, onClick = onRestart)
                    GameButton(art, "Hint", Modifier.weight(1f, fill = false), small = true, enabled = enabled, onClick = onHint)
                }
            }
        }
    }
}

@Composable private fun GameStatus(art: Artwork, round: Round, horizontal: Boolean) {
    val level = round.level
    val rating = stars(level.par, round.moves, round.hints)
    Column(Modifier.enamelFrame(art.sprite("panel_green"), 14).padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalAlignment = if (horizontal) Alignment.Start else Alignment.CenterHorizontally) {
        TextLine("Level ${level.number}", 17, cream, FontWeight.ExtraBold)
        TextLine("${round.moves} moves  •  ${round.water.watered}/${level.plants} watered", 13, mint)
        Stars(art, rating, 16)
    }
}

@Composable private fun GamePopup(art: Artwork, victory: Boolean, clock: State<Float>, content: @Composable ColumnScope.() -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance = animateFloatAsState(if (entered) 1f else .92f,
        spring(dampingRatio = .72f, stiffness = 250f), label = "panel entrance")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xA4021721)).windowInsetsPadding(WindowInsets.displayCutout)
        .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        if (victory) Confetti(clock)
        val availableHeight = maxHeight
        val compact = maxHeight < 410.dp
        Column(Modifier.widthIn(max = if (compact) 330.dp else 352.dp).fillMaxWidth()
            .heightIn(max = availableHeight).graphicsLayer {
                scaleX = entrance.value
                scaleY = entrance.value
            }.enamelFrame(art.sprite("panel_green"), 30)
            .padding(horizontal = 33.dp, vertical = if (compact) 20.dp else 26.dp)
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            CompositionLocalProvider(CompactChrome provides compact) { content() }
        }
    }
}

@Composable private fun Confetti(clock: State<Float>) {
    val start = remember { clock.value }
    val colors = remember { listOf(Color(0xFFFFD567), Color(0xFF79F6FF), Color(0xFFF08FD3), Color(0xFF8BFFA9)) }
    var running by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(4300); running = false }
    if (!running) return
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value - start
        val fade = ((4.3f - t) / .85f).coerceIn(0f, 1f)
        for (i in 0..36) {
            val phase = (t * (.27f + (i % 4) * .043f) + ((i * 11) % 37) / 37f) % 1f
            val x = size.width * (((i * 23) % 37) / 37f + .036f * sin(t * 2.2f + i))
            val y = size.height * phase
            rotate(360f * phase + i * 29f, Offset(x, y)) {
                drawRoundRect(colors[i % 4].copy(alpha = fade * (1f - phase * .52f)), Offset(x, y),
                    Size((3 + i % 5).dp.toPx(), (4 + i % 7).dp.toPx()), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

@Composable internal fun Stars(art: Artwork, earned: Int, size: Int, clock: State<Float>? = null) {
    val started = remember { clock?.value ?: 0f }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (index in 0..2) Image(art.sprite("star"), null, Modifier.size(size.dp).graphicsLayer {
            alpha = if (index < earned) 1f else .24f
            if (clock != null && index < earned) {
                val delta = (clock.value - started - index * .11f).coerceAtLeast(0f)
                val growth = (delta * 5f).coerceIn(0f, 1f)
                val bounce = (sin(delta * 12f) * .21f * (1f - delta).coerceAtLeast(0f))
                scaleX = growth + bounce
                scaleY = scaleX
            }
        })
    }
}
@Composable internal fun Title(value: String) = TextLine(value, 28, cream, FontWeight.ExtraBold)
@Composable internal fun Caption(value: String, modifier: Modifier = Modifier) = TextLine(value, 14, mint, FontWeight.SemiBold, modifier)
@Composable internal fun TextLine(value: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    BasicText(value, modifier, overflow = TextOverflow.Clip, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = FontFamily.SansSerif, letterSpacing = (if (weight >= FontWeight.ExtraBold) .35 else 0.0).sp,
        textAlign = TextAlign.Center, lineHeight = (size + 2).sp,
        shadow = androidx.compose.ui.graphics.Shadow(Color(0xD907181F), Offset(0f, 2f), 3f)))
}
