package ru.letniev.chats

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

// ───────── тема ─────────

@Immutable
private class Pal(
    val primary: Color, val onPrimary: Color, val surface: Color, val navBg: Color, val title: Color,
    val text1: Color, val text2: Color, val mute: Color, val divider: Color, val headerGray: Color
)

private val LightPal = Pal(
    primary = Color(0xFF8F5FE8), onPrimary = Color.White, surface = Color(0xFFFDFBFF), navBg = Color(0xFFF8F5FC),
    title = Color.Black, text1 = Color(0xFF1C1B22), text2 = Color(0xFF6F6A7A), mute = Color(0xFFC4BECF),
    divider = Color(0xFFE4E1EA), headerGray = Color(0xFFB4AEBF)
)
private val DarkPal = Pal(
    primary = Color(0xFFB794F6), onPrimary = Color(0xFF21093F), surface = Color(0xFF121016), navBg = Color(0xFF1B1820),
    title = Color.White, text1 = Color(0xFFECE8F2), text2 = Color(0xFF9F99AB), mute = Color(0xFF8A8497),
    divider = Color(0xFF2B2833), headerGray = Color(0xFF8A8497)
)

// плавный переход между темами: t = 0 светлая, 1 тёмная
private fun mix(t: Float) = Pal(
    lerpColor(LightPal.primary, DarkPal.primary, t), lerpColor(LightPal.onPrimary, DarkPal.onPrimary, t),
    lerpColor(LightPal.surface, DarkPal.surface, t), lerpColor(LightPal.navBg, DarkPal.navBg, t),
    lerpColor(LightPal.title, DarkPal.title, t), lerpColor(LightPal.text1, DarkPal.text1, t),
    lerpColor(LightPal.text2, DarkPal.text2, t), lerpColor(LightPal.mute, DarkPal.mute, t),
    lerpColor(LightPal.divider, DarkPal.divider, t), lerpColor(LightPal.headerGray, DarkPal.headerGray, t)
)

private val LocalPal = compositionLocalOf { LightPal }

// ───────── текст ─────────

private fun ts(size: Int, lh: Int, w: Int, ls: Float, c: Color) = TextStyle(
    fontSize = size.sp, lineHeight = lh.sp, fontWeight = FontWeight(w), letterSpacing = ls.sp, color = c,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

private val LocalFF = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

// Roboto подкладывает сборка (см. workflow); если файлов нет, берём системный шрифт
private fun loadFont(c: Context): FontFamily = try {
    val a = c.assets
    val l = a.list("fonts")?.toList().orEmpty()
    if ("Roboto-Regular.ttf" in l && "Roboto-Medium.ttf" in l)
        FontFamily(Font("fonts/Roboto-Regular.ttf", a, FontWeight.Normal), Font("fonts/Roboto-Medium.ttf", a, FontWeight.Medium))
    else FontFamily.Default
} catch (e: Exception) { FontFamily.Default }

@Composable
private fun T(s: String, st: TextStyle, m: Modifier = Modifier, lines: Int = 1, align: TextAlign? = null) =
    BasicText(s, m, st.copy(textAlign = align ?: TextAlign.Unspecified, fontFamily = LocalFF.current), maxLines = lines, overflow = TextOverflow.Ellipsis)

private const val STORIES = 112  // высота ряда очереди, dp
private const val FADE = 32       // высота мягкого затухания под шапкой, dp
private const val LIST_GAP = 6    // на сколько список заходит под затухание, dp
private const val SHOWN = 3      // сколько человек из очереди показываем отдельно, остальные уходят в кружок «+N»

// ───────── резинка вместо андроидовского оверскролла ─────────
// Тянешь за край: содержимое идёт за пальцем, но туго, и чем дальше, тем сильнее сопротивление.
// Отпустил: плавно возвращается без отскока. Работает по вертикали (список) и по горизонтали (ряд очереди).
@Stable
private class Rubber(private val scope: CoroutineScope, u: Float, private val horizontal: Boolean = false) : NestedScrollConnection {
    private var raw by mutableFloatStateOf(0f)   // сколько реально протянул палец, px
    private var job: Job? = null
    private val limit = 56f * u                  // дальше этого не уедет никогда
    private val grip = .3f                       // сопротивление в самом начале: 1 = без сопротивления

    val offset: Float get() = if (raw == 0f) 0f else sign(raw) * limit * (1f - exp(-abs(raw) * grip / limit))

    private fun Offset.a() = if (horizontal) x else y
    private fun Velocity.a() = if (horizontal) x else y
    private fun off(v: Float) = if (horizontal) Offset(v, 0f) else Offset(0f, v)

    private fun springBack(v: Float = 0f, from: Float = raw) {
        job?.cancel()
        job = scope.launch { animate(from, 0f, v, spring(1f, 220f)) { x, _ -> raw = x } }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || raw == 0f) return Offset.Zero
        job?.cancel()
        val n = raw + available.a()
        return if (raw * n <= 0f) { // палец вернулся за исходную точку: забираем ровно до нуля
            val c = -raw; raw = 0f; off(c)
        } else { raw = n; off(available.a()) }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.a() == 0f) return Offset.Zero
        job?.cancel()
        raw += available.a()
        return off(available.a())
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (raw == 0f) return Velocity.Zero
        springBack()
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.a() != 0f && raw == 0f) springBack(available.a() * .2f, 0f)
        return Velocity.Zero
    }
}

// ───────── сворачивание очереди ─────────
// Без хитростей: высота ряда очереди ровно следует за пальцем (1:1), без порогов и рывков.
// Отпустил на полпути: пружиной доезжает до ближайшего положения (по броску: туда, куда бросил).
@Stable
private class HeaderState(val maxPx: Float, private val scope: CoroutineScope) : NestedScrollConnection {
    var collapsed by mutableFloatStateOf(0f)
    val p: Float get() = if (maxPx > 0f) (collapsed / maxPx).coerceIn(0f, 1f) else 0f
    private var job: Job? = null

    // d > 0 сворачивает, d < 0 раскрывает; возвращает, сколько реально сдвинулось
    fun by(d: Float): Float {
        job?.cancel()
        val n = (collapsed + d).coerceIn(0f, maxPx)
        val used = n - collapsed
        collapsed = n
        return used
    }

    fun settle(vy: Float) { // vy: скорость пальца, вверх отрицательно
        if (collapsed <= 0f || collapsed >= maxPx) return
        val target = when { vy < -700f -> maxPx; vy > 700f -> 0f; p > .5f -> maxPx; else -> 0f }
        job?.cancel()
        job = scope.launch { animate(collapsed, target, -vy.coerceIn(-4000f, 4000f), spring(1f, 300f)) { x, _ -> collapsed = x.coerceIn(0f, maxPx) } }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        return if (dy < 0f && collapsed < maxPx) Offset(0f, -by(-dy)) else Offset.Zero
    }

    // список уже у верха, палец тянет вниз дальше: раскрываем очередь
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        return if (source == NestedScrollSource.UserInput && dy > 0f && collapsed > 0f) Offset(0f, -by(-dy)) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity { settle(available.y); return Velocity.Zero }
}

// ───────── экран ─────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(dark: Boolean, onToggleTheme: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val list = when (tab) { 1 -> chats.filter { it.badge != null }; 2 -> chats.filter { it.shared }; else -> chats }
    val ff = remember { loadFont(ctx) }
    val u = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val rubber = remember(u) { Rubber(scope, u) }
    val hs = remember(u) { HeaderState(STORIES * u, scope) }
    val t by animateFloatAsState(if (dark) 1f else 0f, tween(350), label = "theme")
    val pal = mix(t)
    val toggle by rememberUpdatedState(onToggleTheme)
    val haptic = LocalHapticFeedback.current
    var headerH by remember { mutableIntStateOf(0) }
    var titleW by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalFF provides ff, LocalPal provides pal, LocalOverscrollConfiguration provides null) {
        Column(
            Modifier.fillMaxSize().background(pal.surface)
                // удержание на пустом месте (на строках чатов и кнопках не срабатывает: они забирают нажатие себе)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); toggle() }) }
                .nestedScroll(rubber)
                .nestedScroll(hs)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                LazyColumn(Modifier.fillMaxSize().graphicsLayer { translationY = rubber.offset }) {
                    // место под шапку: следует за её высотой, пока очередь сворачивается
                    item {
                        Spacer(Modifier.fillMaxWidth().layout { _, c ->
                            layout(c.maxWidth, (headerH - LIST_GAP.dp.roundToPx()).coerceAtLeast(0)) {}
                        })
                    }
                    items(list) { ChatRow(it) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                // шапка без плотной подложки и границы: полупрозрачный фейд, список мягко уходит под него
                val sf = pal.surface
                val fade = remember(sf) { (0..12).map { k -> val t = k / 12f; sf.copy(alpha = .98f * (1f - t * t * (3f - 2f * t))) } }
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .onSizeChanged { headerH = it.height }
                        .drawBehind {
                            val f = FADE.dp.toPx().coerceAtMost(size.height)
                            val top = size.height - f
                            drawRect(sf.copy(alpha = .98f), Offset.Zero, Size(size.width, top))
                            drawRect(Brush.verticalGradient(fade, startY = top, endY = size.height), Offset(0f, top), Size(size.width, f))
                        }
                ) {
                    Column {
                        Column(Modifier.statusBarsPadding()) { Header(tab, { tab = it }, hs, { titleW }, { titleW = it }) { toggle() } }
                        Spacer(Modifier.height(FADE.dp))
                    }
                }
            }
            NavBar()
        }
    }
}

@Composable
private fun IconBox(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = Modifier.size(48.dp).clip(CircleShape)
    Box(
        if (onClick != null) m.clickable(remember { MutableInteractionSource() }, null) { onClick() } else m,
        Alignment.Center
    ) { content() }
}

// шапка: архив слева, заголовок по центру, поиск справа, ряд очереди и вкладки.
// Очередь сворачивается вместе с прокруткой списка; шапку можно двигать и пальцем за неё саму.
@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, hs: HeaderState, titleW: () -> Int, onTitle: (Int) -> Unit, onArchive: () -> Unit) {
    val pal = LocalPal.current
    val drag = rememberDraggableState { d -> hs.by(-d) }
    Column(Modifier.fillMaxWidth().draggable(drag, Orientation.Vertical, onDragStopped = { v -> hs.settle(v) })) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { IconBox(onArchive) { VIcon(ARCHIVE, pal.headerGray, 28.dp, stroke = 1.7f) } }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { IconBox { VIcon(SEARCH, pal.primary, 28.dp, stroke = 2f) } }
            Box(Modifier.align(Alignment.Center).onSizeChanged { onTitle(it.width) }.graphicsLayer { translationX = 33f * density * hs.p }) { T("Общение", ts(20, 28, 500, 0f, pal.title)) }
        }
        Stories(hs, titleW)
        Tabs(tab, onTab)
    }
}

// очередь «можно взять»: все кружки без колец, листаются вбок.
// Сворачивание как у Telegram: подписи гаснут, кружки уменьшаются и стягиваются стопкой слева от заголовка.
// Ничего не обрезается: кружки рисуются поверх, обрезки у ряда нет.
@Composable
private fun Stories(hs: HeaderState, titleW: () -> Int) {
    val d = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().layout { m, c ->
            val h = (hs.maxPx - hs.collapsed).roundToInt().coerceAtLeast(0)
            val pl = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(pl.width, h) { pl.place(0, 0) }
        }
    ) {
        val w = with(d) { maxWidth.toPx() }
        val itemW = w / 4.4f
        val pad = 12f * d.density
        val maxSx = maxOf(0f, pad * 2 + itemW * queueChats.size - w)
        var sx by remember { mutableFloatStateOf(0f) }
        val state = rememberScrollableState { dd -> val old = sx; sx = (sx - dd).coerceIn(0f, maxSx); old - sx }
        val canScroll by remember { derivedStateOf { hs.collapsed < 1f } }
        Box(Modifier.fillMaxWidth().height(STORIES.dp).scrollable(state, Orientation.Horizontal, enabled = canScroll)) {
            queueChats.forEachIndexed { i, c -> StoryItem(i, c, hs, { sx }, itemW, pad, w, titleW) }
        }
    }
}

@Composable
private fun StoryItem(i: Int, c: Chat, hs: HeaderState, sx: () -> Float, itemW: Float, pad: Float, w: Float, titleW: () -> Int) {
    val pal = LocalPal.current
    val d = LocalDensity.current
    val left = pad + itemW * i
    val small = 26f / 64f
    Column(
        Modifier.offset { IntOffset(left.roundToInt(), 0) }.width(with(d) { itemW.toDp() }).zIndex(20f - i).padding(top = 4.dp)
            .graphicsLayer {
                val p = hs.p
                val s = sx()
                val stackW = 58f * density; val gap = 8f * density
                val gl = w / 2 - (stackW + gap + titleW()) / 2
                val tgt = gl + 13f * density + 16f * density * minOf(i, 2)
                val cur = left + itemW / 2 - s
                translationX = -s + (tgt - cur) * p
                translationY = -68f * density * p
                alpha = if (i < 3) 1f else (1f - (p - .1f) * 2f).coerceIn(0f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.graphicsLayer { val k = 1f - (1f - small) * hs.p; scaleX = k; scaleY = k }
                .drawBehind {
                    val p = hs.p
                    if (p > 0f) drawCircle(pal.surface, size.minDimension / 2 + p * 2.dp.toPx() / (1f - (1f - small) * p))
                }
        ) { Avatar(c.init, 64, 23, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2)))) }
        Column(Modifier.graphicsLayer { alpha = (1f - hs.p * 3f).coerceIn(0f, 1f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            T(c.name, ts(14, 18, 500, 0f, pal.text1))
            T(c.time, ts(13, 16, 500, 0f, if (i < 2) pal.primary else pal.text2))
        }
    }
}

@Composable
private fun Tabs(sel: Int, onSel: (Int) -> Unit) {
    val pal = LocalPal.current
    val names = listOf("все", "непрочитанные", "общие")
    val xs = remember { mutableStateListOf(0f, 0f, 0f) }
    val ws = remember { mutableStateListOf(0f, 0f, 0f) }
    val x by animateFloatAsState(xs[sel], tween(320), label = "x")
    val w by animateFloatAsState(ws[sel], tween(320), label = "w")
    // островок: едва заметная подложка, выбранная вкладка чуть ярче
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), Alignment.Center) {
        val island = lerpColor(pal.surface, pal.text1, .10f)
        Box(Modifier.clip(RoundedCornerShape(21.dp)).background(island).padding(3.dp)) {
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), 0) }.align(Alignment.CenterStart)
                    .width(with(LocalDensity.current) { w.toDp() }).height(36.dp)
                    .clip(RoundedCornerShape(18.dp)).background(lerpColor(island, pal.primary, .26f))
            )
            Row {
                names.forEachIndexed { i, n ->
                    val on = i == sel
                    val col by animateColorAsState(if (on) pal.primary else pal.text2, label = "c")
                    Row(
                        Modifier.height(36.dp)
                            .onGloballyPositioned { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(remember { MutableInteractionSource() }, null) { onSel(i) }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        T(n, ts(15, 22, 500, .1f, col))
                        if (i == 0) Box(
                            Modifier.defaultMinSize(18.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) pal.primary else pal.mute).padding(horizontal = 5.dp),
                            Alignment.Center
                        ) { T(chats.count { it.badge != null }.toString(), ts(11, 15, 500, 0f, if (on) pal.onPrimary else pal.surface)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(init: String, size: Int, fs: Int, photo: String, brush: Brush) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(brush), Alignment.Center) {
        T(init, ts(fs, fs + 2, 500, 0f, Color.White))
        AsyncImage(photo, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
private fun ChatRow(c: Chat) {
    val pal = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // три строки: имя, ниже до двух строк текста (или отправитель и одна строка), без разделителей
    Row(
        Modifier.fillMaxWidth().height(84.dp).background(if (pressed) pal.primary.copy(alpha = .09f) else Color.Transparent)
            .clickable(src, null) {}.padding(start = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.padding(top = 11.dp).size(62.dp)) {
            Avatar(c.init, 62, 22, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2))))
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(top = 9.dp, end = 12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                        T(c.name, ts(17, 22, 500, 0f, pal.text1), Modifier.weight(1f, fill = false))
                        if (c.muted) Box {
                            VIcon(SPEAKER, pal.mute, 17.dp)
                            VIcon(CROSS, pal.mute, 17.dp, stroke = 1.6f)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (c.check) VIcon(CHECK, pal.primary, 18.dp)
                        T(c.time, ts(14, 22, 400, 0f, pal.text2))
                    }
                }
                Column(Modifier.padding(end = if (c.badge != null) 36.dp else 0.dp)) {
                    if (c.sender != null) T(c.sender, ts(15, 20, 400, 0f, pal.text1))
                    T(c.text, ts(15, 20, 400, 0f, pal.text2), lines = if (c.sender != null) 1 else 2)
                }
            }
            if (c.badge != null) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 46.dp).defaultMinSize(22.dp).height(22.dp)
                    .clip(RoundedCornerShape(11.dp)).background(if (c.orange) pal.primary else pal.mute).padding(horizontal = 6.dp),
                Alignment.Center
            ) { T(c.badge, ts(13, 18, 500, 0f, if (c.orange) pal.onPrimary else pal.surface)) }
        }
    }
}

@Composable
private fun NavBar() {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().background(pal.navBg).padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 14.dp).navigationBarsPadding()) {
        NavItem("Стена", false) { VIcon(BELL_O, pal.text2, 28.dp, evenOdd = true) }
        NavItem("Чаты", true) { VIcon(CHAT, pal.primary, 28.dp) }
        NavItem("Профиль", false) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFC9A8FF), Color(0xFF8F5FE8)))), Alignment.Center) {
                T("И", ts(14, 20, 500, .1f, Color.White))
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, on: Boolean, icon: @Composable () -> Unit) {
    val pal = LocalPal.current
    Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) {}, Arrangement.spacedBy(2.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp, 38.dp), Alignment.Center) { icon() }
        T(label, ts(12, 16, 500, .5f, if (on) pal.primary else pal.text2))
    }
}
