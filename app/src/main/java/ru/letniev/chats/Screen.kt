package ru.letniev.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

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

private const val BAR = 76      // высота строки с заголовком, dp
private const val STORIES = 104  // высота очереди в развёрнутом виде, dp
private const val STACK_W = 60   // ширина стопки из трёх аватарок в свёрнутом виде, dp
private const val STACK_GAP = 10 // зазор между стопкой и заголовком, dp

// состояние шапки: два положения (очередь развёрнута / свёрнута).
// Шапка идёт за пальцем всё время: сначала с сопротивлением, дальше почти 1:1; после отпускания доезжает пружиной,
// продолжая движение пальца (дальше трети пути или по броску), иначе возвращается назад.
@Stable
private class HeaderState(val maxPx: Float, private val dpu: Float, private val scope: CoroutineScope) {
    var collapsed by mutableFloatStateOf(0f)
    val p: Float get() = if (maxPx > 0f) (collapsed / maxPx).coerceIn(0f, 1f) else 0f
    var isCollapsed = false
        private set
    var pull = 0f
        private set
    private var job: Job? = null
    private val busy get() = job?.isActive == true
    private var cv = 0f        // скорость шапки, px/с
    private var lastT = 0L
    private val reach = 40f * dpu
    private val k0 = .5f
    private val stiffness = 311f   // критическое демпфирование, ~0.34 с
    private val trigger = 80f * dpu  // сколько надо реально протянуть палец вниз, чтобы очередь раскрылась сама
    private val rubber = 26f * dpu   // насколько шапка вообще сдвигается, пока тянешь до порога

    // сколько шапка прошла, когда палец протянул x: в начале наклон k0, дальше плавно до 1:1
    private fun cOf(x: Float): Float {
        val u = x / reach
        val q = if (u <= 1f) reach * (u * u * u - u * u * u * u / 2f) else reach * .5f + (x - reach)
        return k0 * x + (1f - k0) * q
    }

    private fun springTo(t: Float, v0: Float) {
        job?.cancel()
        job = scope.launch { animate(collapsed, t, v0, spring(1f, stiffness)) { v, _ -> collapsed = v.coerceIn(0f, maxPx) } }
    }
    fun flip(to: Boolean, minV: Float = 0f) {
        val dir = if (to) 1f else -1f
        isCollapsed = to; pull = 0f
        springTo(if (to) maxPx else 0f, dir * maxOf(cv * dir, minV * dpu))
    }

    // d > 0: тянем в сторону сворачивания. Возвращает, сколько движения забрала шапка (остальное уйдёт списку)
    fun drag(d: Float): Float {
        if (busy) return d
        val away = if (isCollapsed) -d else d
        if (pull == 0f && away <= 0f) return 0f
        val prev = collapsed
        val now = System.nanoTime()
        pull = (pull + away).coerceAtLeast(0f)
        if (isCollapsed) {
            // раскрытие как в Telegram: сначала шапка почти не поддаётся (резинка), дотянул до порога — раскрывается сама
            if (pull >= trigger) { cv = 0f; flip(false, 450f); return d }
            collapsed = maxPx - rubber * (1f - exp(-pull / (trigger * .5f)))
            cv = 0f; lastT = now
            return d
        }
        if (cOf(pull) >= maxPx) { // дотянули пальцем до конца
            isCollapsed = !isCollapsed; pull = 0f; collapsed = if (isCollapsed) maxPx else 0f; cv = 0f
            return d
        }
        val c = cOf(pull)
        collapsed = if (isCollapsed) maxPx - c else c
        val dt = maxOf(4f, (now - lastT) / 1_000_000f) / 1000f
        cv = cv * .5f + ((collapsed - prev) / dt) * .5f
        lastT = now
        return d
    }

    // палец отпущен: vy в px/с, вверх отрицательно
    fun release(vy: Float) {
        if (busy || pull == 0f) return
        if (isCollapsed) { pull = 0f; springTo(maxPx, 0f); return } // не дотянул до порога: резинка возвращается, лёгкий свайп не раскрывает
        if ((System.nanoTime() - lastT) / 1_000_000f > 90f) cv = 0f
        val v = 600f * dpu
        val flick = (!isCollapsed && vy < -v) || (isCollapsed && vy > v)
        val back = (isCollapsed && vy < -v) || (!isCollapsed && vy > v)
        if (!back && (flick || cOf(pull) > maxPx * .3f)) flip(!isCollapsed, if (flick) 500f else 0f)
        else { pull = 0f; springTo(if (isCollapsed) maxPx else 0f, cv) }
    }
}

private fun Modifier.storiesHeight(hs: HeaderState) = layout { m, c ->
    val h = (hs.maxPx - hs.collapsed).roundToInt().coerceIn(c.minHeight, c.maxHeight)
    val pl = m.measure(Constraints(c.minWidth, c.maxWidth, h, h))
    layout(pl.width, pl.height) { pl.place(0, 0) }
}

@Composable
fun ChatsScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val list = when (tab) { 1 -> chats.filter { it.badge != null }; 2 -> chats.filter { it.shared }; else -> chats }
    val ff = remember { loadFont(ctx) }
    val u = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val hs = remember(u) { HeaderState(STORIES * u, u, scope) }
    val conn = remember(hs) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (hs.pull > 0f || (dy < 0f && !hs.isCollapsed)) return Offset(0f, -hs.drag(-dy))
                return Offset.Zero
            }
            // список уже у верха, а палец тянет вниз дальше: тянем очередь
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy > 0f && hs.isCollapsed) return Offset(0f, -hs.drag(-dy))
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { hs.release(available.y); return Velocity.Zero }
        }
    }
    CompositionLocalProvider(LocalFF provides ff) { Column(Modifier.fillMaxSize().background(Surface).nestedScroll(conn)) {
        Column(Modifier.statusBarsPadding()) { Header(tab, { tab = it }, hs) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list) { ChatRow(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        NavBar()
    } }
}

@Composable
private fun IconBox(content: @Composable () -> Unit) =
    Box(Modifier.size(48.dp), Alignment.Center) { content() }

// шапка: архив слева, заголовок по центру, поиск справа, очередь «сторис» и вкладки; при сворачивании очередь становится стопкой слева от заголовка
@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, hs: HeaderState) {
    val u = LocalDensity.current.density
    val ss = rememberScrollState()
    val n = queueChats.size
    val showHit by remember(hs) { derivedStateOf { hs.p > .9f } }
    val canScroll by remember(hs) { derivedStateOf { hs.p < .3f } }
    val drag = rememberDraggableState { d -> hs.drag(-d) }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Surface).draggable(drag, Orientation.Vertical, onDragStopped = { v -> hs.release(v) })) {
        var titlePx by remember { mutableIntStateOf(0) }
        val stackPx = (STACK_W + STACK_GAP) * u
        val groupLeft = (constraints.maxWidth - stackPx - titlePx) / 2f  // левый край группы «стопка + заголовок»
        Column {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp)) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { IconBox { VIcon(ARCHIVE, HeaderGray, 28.dp, stroke = 1.7f) } }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { IconBox { VIcon(SEARCH, Primary, 28.dp, stroke = 2f) } }
                // заголовок по центру; когда очередь свёрнута, стопка встаёт слева от него и они вместе стоят по центру
                Box(Modifier.align(Alignment.Center).graphicsLayer { translationX = hs.p * stackPx / 2f }) {
                    T("Общение", ts(20, 28, 500, 0f, Color.Black), Modifier.onSizeChanged { titlePx = it.width })
                }
            }
            Spacer(Modifier.fillMaxWidth().storiesHeight(hs))
            Tabs(tab, onTab)
        }

        // всё завязано на один прогресс p: шапка, вкладки, список, аватарки и подписи двигаются вместе
        Box(Modifier.offset(y = BAR.dp).fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
            queueChats.forEachIndexed { i, c ->
                Box(
                    Modifier.padding(top = 64.dp).width(64.dp).graphicsLayer {
                        val p = hs.p
                        translationX = (16 + i * 72) * u - ss.value * (1 - p) - 4 * u
                        translationY = -50f * u * p
                        alpha = 1f - (p / .3f).coerceIn(0f, 1f)
                    },
                    Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(c.name, ts(12, 16, 500, 0f, Text1))
                        T(c.time, ts(11, 16, 400, 0f, if (i < 2) Primary else Text2))
                    }
                }
            }
        }

        // аватары летят по прямой в стопку из первых трёх; остальные по пути растворяются
        queueChats.forEachIndexed { i, c ->
            Box(
                Modifier
                    .graphicsLayer {
                        val p = hs.p
                        val x0 = (16 + i * 72) * u - ss.value * (1 - p)
                        val x1 = groupLeft + minOf(i, 2) * 16 * u
                        translationX = x0 + (x1 - x0) * p
                        translationY = ((BAR + 4) + ((BAR - 46) - (BAR + 4)) * p) * u
                        val s = 1f - .5f * p
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                        alpha = if (i < 3) 1f else 1f - ((p - .1f) / .5f).coerceIn(0f, 1f)
                    }
                    .zIndex((n - i).toFloat())
                    .size(56.dp)
                    .drawBehind {
                        // кольцо цвета фона отделяет аватарки в стопке; в развёрнутом виде его нет
                        val ra = ((hs.p - .6f) / .4f).coerceIn(0f, 1f)
                        if (ra > 0f) drawCircle(Surface.copy(alpha = ra), size.minDimension / 2 + 3.dp.toPx())
                    }
            ) {
                Avatar(c.init, 56, 20, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2))))
            }
        }

        if (canScroll) Box(Modifier.offset(y = BAR.dp).fillMaxWidth().storiesHeight(hs).horizontalScroll(ss)) {
            Spacer(Modifier.width((16 + n * 72 - 8 + 16).dp).height(1.dp))
        }

        // тап по стопке аватаров возвращает очередь
        if (showHit) Box(
            Modifier.offset { IntOffset(groupLeft.roundToInt(), 0) }.size((STACK_W + 8).dp, BAR.dp)
                .clickable(remember { MutableInteractionSource() }, null) { hs.flip(false, 300f) }
        )
    }
}

@Composable
private fun Tabs(sel: Int, onSel: (Int) -> Unit) {
    val names = listOf("все", "непрочитанные", "общие")
    val xs = remember { mutableStateListOf(0f, 0f, 0f) }
    val ws = remember { mutableStateListOf(0f, 0f, 0f) }
    val x by animateFloatAsState(xs[sel], tween(320), label = "x")
    val w by animateFloatAsState(ws[sel], tween(320), label = "w")
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
      Box(Modifier.padding(horizontal = 10.dp).height(48.dp)) {
        // пилюля выбранной папки: едет под текстом от вкладки к вкладке
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.align(Alignment.CenterStart)
                .width(with(LocalDensity.current) { w.toDp() }).height(34.dp)
                .clip(RoundedCornerShape(17.dp)).background(Primary.copy(alpha = .13f))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            names.forEachIndexed { i, n ->
                val on = i == sel
                val col by animateColorAsState(if (on) Primary else Text2, label = "c")
                Row(
                    Modifier.height(48.dp)
                        .onGloballyPositioned { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                        .clickable(remember { MutableInteractionSource() }, null) { onSel(i) }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    T(n, ts(16, 24, 500, .1f, col))
                    if (i == 0) Box(
                        Modifier.defaultMinSize(20.dp).height(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Primary else Mute).padding(horizontal = 6.dp),
                        Alignment.Center
                    ) { T(chats.count { it.badge != null }.toString(), ts(12, 16, 500, 0f, if (on) Color.White else Surface)) }
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
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // как в iOS: аватарка 62, текст с 80dp, тонкий разделитель от начала текста до правого края
    Row(
        Modifier.fillMaxWidth().height(78.dp).background(if (pressed) Primary.copy(alpha = .09f) else Color.Transparent)
            .drawBehind { drawLine(Divider, Offset(80.dp.toPx(), size.height), Offset(size.width, size.height), strokeWidth = .5.dp.toPx()) }
            .clickable(src, null) {}.padding(start = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.padding(top = 8.dp).size(62.dp)) {
            Avatar(c.init, 62, 22, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2))))
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, end = 12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                        T(c.name, ts(17, 22, 500, 0f, Text1), Modifier.weight(1f, fill = false))
                        if (c.muted) Box {
                            VIcon(SPEAKER, Mute, 17.dp)
                            VIcon(CROSS, Mute, 17.dp, stroke = 1.6f)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (c.check) VIcon(CHECK, Primary, 18.dp)
                        T(c.time, ts(14, 22, 400, 0f, Text2))
                    }
                }
                Column(Modifier.padding(end = if (c.badge != null) 36.dp else 0.dp)) {
                    if (c.sender != null) T(c.sender, ts(15, 20, 400, 0f, Text1))
                    T(c.text, ts(15, 20, 400, 0f, Text2), lines = if (c.sender != null) 1 else 2)
                }
            }
            if (c.badge != null) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 35.dp).defaultMinSize(22.dp).height(22.dp)
                    .clip(RoundedCornerShape(11.dp)).background(if (c.orange) Primary else Mute).padding(horizontal = 6.dp),
                Alignment.Center
            ) { T(c.badge, ts(13, 18, 500, 0f, if (c.orange) Color.White else Surface)) }
        }
    }
}

@Composable
private fun NavBar() {
    Row(Modifier.fillMaxWidth().background(NavBg).padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 14.dp).navigationBarsPadding()) {
        NavItem("Стена", false) { VIcon(BELL_O, Text2, 28.dp, evenOdd = true) }
        NavItem("Чаты", true) { VIcon(CHAT, Primary, 28.dp) }
        NavItem("Профиль", false) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFC9A8FF), Color(0xFF8F5FE8)))), Alignment.Center) {
                T("И", ts(14, 20, 500, .1f, Color.White))
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, on: Boolean, icon: @Composable () -> Unit) {
    Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) {}, Arrangement.spacedBy(2.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp, 38.dp), Alignment.Center) { icon() }
        T(label, ts(12, 16, 500, .5f, if (on) Primary else Text2))
    }
}
