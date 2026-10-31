package ru.letniev.chats

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
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

private const val STORIES = 130  // на сколько сворачивается блок «Можно взять» (его высота + отступ снизу), dp
private const val BLOCK_H = 130   // полная высота самого блока, dp
private const val IT = 76f        // шаг между кружками очереди, dp
private const val AV = 56f        // диаметр кружка очереди, dp
private const val SM = 26f / 56f  // масштаб кружка в свёрнутом виде (стопка рядом с заголовком)
private const val RESIST = .5f    // насколько туго раскрывается блок: на 1 dp пальца 0.5 dp блока

private fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
// у каждого кружка свой лёгкий сдвиг по времени: стягиваются к заголовку не разом, а волной
private fun stackE(p: Float, i: Int) = smooth(p * 1.15f - i * .03f)

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
// Сворачивание идёт за пальцем 1:1. Раскрытие тянется туго (RESIST): блок идёт за пальцем с сопротивлением,
// а когда отпустил, сам плавно доезжает до конца (хватит пятой части пути или броска вниз).
// Не дотянул: так же плавно возвращается свёрнутым.
@Stable
private class HeaderState(val maxPx: Float, private val scope: CoroutineScope) : NestedScrollConnection {
    var collapsed by mutableFloatStateOf(0f)
    val p: Float get() = if (maxPx > 0f) (collapsed / maxPx).coerceIn(0f, 1f) else 0f
    private var job: Job? = null
    private var rest = 0f   // в каком положении блок стоял до этого касания: 0 раскрыт, maxPx свёрнут

    // d > 0 сворачивает, d < 0 раскрывает (туго); возвращает, сколько пальца реально израсходовано
    fun by(d: Float): Float {
        job?.cancel()
        val k = if (d < 0f) RESIST else 1f
        val n = (collapsed + d * k).coerceIn(0f, maxPx)
        val used = n - collapsed
        collapsed = n
        return used / k
    }

    fun settle(vy: Float) { // vy: скорость пальца, вверх отрицательно
        if (collapsed <= 0f) { rest = 0f; return }
        if (collapsed >= maxPx) { rest = maxPx; return }
        val open = when {
            vy > 700f -> true
            vy < -700f -> false
            rest >= maxPx -> (1f - p) > .2f || vy > 350f   // был свёрнут: хватит небольшой протяжки
            else -> !(p > .3f || vy < -350f)
        }
        val target = if (open) 0f else maxPx
        rest = target
        job?.cancel()
        job = scope.launch {
            animate(collapsed, target, -vy.coerceIn(-3000f, 3000f) * .5f, spring(.9f, 180f)) { x, _ -> collapsed = x.coerceIn(0f, maxPx) }
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        return if (dy < 0f && collapsed < maxPx) Offset(0f, -by(-dy)) else Offset.Zero
    }

    // список уже у верха, палец тянет вниз дальше: раскрываем очередь (с сопротивлением)
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

    CompositionLocalProvider(LocalFF provides ff, LocalPal provides pal, LocalOverscrollConfiguration provides null) {
        Column(
            Modifier.fillMaxSize().background(pal.surface)
                // удержание на пустом месте (на строках чатов и кнопках не срабатывает: они забирают нажатие себе)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); toggle() }) }
                .nestedScroll(rubber)
                .nestedScroll(hs)
        ) {
            Column(Modifier.statusBarsPadding()) { Header(tab, { tab = it }, hs, rubber) { toggle() } }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                LazyColumn(Modifier.fillMaxSize().graphicsLayer { translationY = rubber.offset }) {
                    items(list) { ChatRow(it) }
                    item { Spacer(Modifier.height(16.dp)) }
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

// шапка: архив слева, заголовок по центру, поиск справа, очередь «можно взять» и папки.
// Очередь без контейнера: кружки лежат прямо на фоне. Они рисуются поверх шапки (оверлеем), поэтому при сворачивании
// плавно стягиваются в стопку рядом с заголовком, как сторис в Telegram. Шапку можно двигать и пальцем за неё саму.
@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, hs: HeaderState, listRubber: Rubber, onArchive: () -> Unit) {
    val pal = LocalPal.current
    val u = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val hRubber = remember(u) { Rubber(scope, u, horizontal = true) }  // край ряда кружков тянется резинкой
    val qs = rememberScrollState()
    var hw by remember { mutableIntStateOf(0) }
    var titleW by remember { mutableIntStateOf(0) }
    val drag = rememberDraggableState { d -> hs.by(-d) }
    Box(Modifier.fillMaxWidth().onSizeChanged { hw = it.width }) {
        Column(Modifier.fillMaxWidth().draggable(drag, Orientation.Vertical, onDragStopped = { v -> hs.settle(v) })) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { IconBox(onArchive) { VIcon(ARCHIVE, pal.headerGray, 28.dp, stroke = 1.7f) } }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { IconBox { VIcon(SEARCH, pal.primary, 28.dp, stroke = 2f) } }
                Box(
                    Modifier.align(Alignment.Center).onSizeChanged { titleW = it.width }
                        .graphicsLayer { translationX = 33f * u * smooth(hs.p) }  // уступает место стопке кружков
                ) { T("Общение", ts(20, 28, 500, 0f, pal.title)) }
            }
            QueueBlock(hs, qs, hRubber)
            Tabs(tab, onTab)
        }
        QueueOverlay(hs, qs, hRubber, listRubber, hw, titleW)
    }
}

// место под очередь: заголовок и невидимая область для горизонтальной прокрутки. Высота идёт за прокруткой.
@Composable
private fun QueueBlock(hs: HeaderState, qs: ScrollState, rubber: Rubber) {
    val pal = LocalPal.current
    Box(
        Modifier.fillMaxWidth().layout { m, c ->
            val h = (hs.maxPx - hs.collapsed).roundToInt().coerceAtLeast(0)
            val pl = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(pl.width, h) { pl.place(0, 0) }
        }
    ) {
        Column(Modifier.fillMaxWidth().height(BLOCK_H.dp)) {
            Row(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = smooth(1f - hs.p * 1.7f)
                    translationY = -hs.collapsed * .25f
                }.padding(start = 16.dp, end = 16.dp, top = 6.dp).height(18.dp),
                Arrangement.Start, Alignment.CenterVertically
            ) { T("Можно взять", ts(13, 18, 500, 0f, pal.text2)) }
            Box(Modifier.fillMaxWidth().height((BLOCK_H - 24).dp).nestedScroll(rubber).horizontalScroll(qs)) {
                Spacer(Modifier.width((22f + IT * queueChats.size).dp).height((BLOCK_H - 24).dp))
            }
        }
    }
}

// кружки и подписи очереди. Положение каждого считается из прогресса сворачивания p:
// развёрнуто: ряд с именем и временем, листается вбок; свёрнуто: стопка из трёх кружков слева от заголовка.
@Composable
private fun QueueOverlay(hs: HeaderState, qs: ScrollState, hR: Rubber, vR: Rubber, hw: Int, titleW: Int) {
    val pal = LocalPal.current
    val u = LocalDensity.current.density
    Box(Modifier.fillMaxWidth()) {
        queueChats.forEachIndexed { i, c ->
            Column(
                Modifier.width(IT.dp).graphicsLayer {
                    val p = hs.p
                    translationX = (11f + IT * i) * u - (qs.value - hR.offset) * (1f - p)
                    translationY = (76f + 92f) * u - p * 18f * u + vR.offset * .6f
                    alpha = smooth(1f - p * 2.4f)
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                T(c.name, ts(13, 16, 500, 0f, pal.text1), Modifier.fillMaxWidth(), align = TextAlign.Center)
                T(c.time, ts(12, 14, 500, 0f, if (i < 2) pal.primary else pal.text2), Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
        queueChats.forEachIndexed { i, c ->
            Box(
                Modifier.size(AV.dp).zIndex(20f - i)
                    .graphicsLayer {
                        val p = hs.p
                        val e = stackE(p, i)
                        val gl = hw / 2f - (66f * u + titleW) / 2f                    // левый край стопки
                        val tx = gl + (13f + 16f * minOf(i, 2)) * u                   // центр кружка в стопке
                        val bx = (11f + IT * i + IT / 2f) * u - (qs.value - hR.offset) * (1f - p)  // центр кружка в ряду
                        val cy = (76f + 58f) * u + (44f - 134f) * u * e + vR.offset * .6f
                        val op = if (i < 3) 1f else smooth(1f - (p - .05f) * 1.8f)
                        var k = (1f - (1f - SM) * e) * (1f + minOf(vR.offset / u / 400f, .08f))
                        if (i >= 3) k *= .6f + .4f * op
                        translationX = bx + (tx - bx) * e - AV * u / 2f
                        translationY = cy - AV * u / 2f
                        scaleX = k; scaleY = k; alpha = op
                    }
                    .drawBehind {
                        // кружок фоновой каёмкой отделяется от соседнего в стопке
                        val e = stackE(hs.p, i)
                        if (e > 0f) drawCircle(pal.surface, size.minDimension / 2f + 2f * u * e / (1f - (1f - SM) * e))
                    }
            ) { Avatar(c.init, AV.toInt(), 21, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2)))) }
        }
    }
}

// папки без плашки под выбранной: выбранная просто цветом, у «все» счётчик непрочитанных
@Composable
private fun Tabs(sel: Int, onSel: (Int) -> Unit) {
    val pal = LocalPal.current
    val names = listOf("все", "непрочитанные", "общие")
    Box(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            names.forEachIndexed { i, n ->
                val on = i == sel
                val col by animateColorAsState(if (on) pal.primary else pal.text2, label = "c")
                Row(
                    Modifier.height(48.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { onSel(i) }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    T(n, ts(16, 24, 500, .1f, col))
                    if (i == 0) Box(
                        Modifier.defaultMinSize(20.dp).height(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) pal.primary else pal.mute).padding(horizontal = 6.dp),
                        Alignment.Center
                    ) { T(chats.count { it.badge != null }.toString(), ts(12, 16, 500, 0f, if (on) pal.onPrimary else pal.surface)) }
                }
            }
        }
    }
}

@Composable
private fun Avatar(init: String, size: Int, fs: Int, photo: String, brush: Brush) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(brush), Alignment.Center) {
        T(init, ts(fs, fs + 2, 500, 0f, Color.White))
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
