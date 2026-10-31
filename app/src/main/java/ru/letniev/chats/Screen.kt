package ru.letniev.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kotlin.math.sin
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

private val StickEasing = CubicBezierEasing(.3f, 0f, .2f, 1.12f)
private const val BAR = 76      // высота строки с заголовком, dp
private const val STORIES = 104  // высота очереди в развёрнутом виде, dp

// состояние шапки: два положения (очередь развёрнута / свёрнута).
// Палец сначала лишь слегка тянет шапку (резинка), после порога она сама доезжает до другого положения.
@Stable
private class HeaderState(val maxPx: Float, private val scope: CoroutineScope) {
    var collapsed by mutableFloatStateOf(0f)
    val p: Float get() = if (maxPx > 0f) (collapsed / maxPx).coerceIn(0f, 1f) else 0f
    var isCollapsed = false
        private set
    var pull = 0f
        private set
    private var job: Job? = null
    private val threshold = maxPx * .16f
    private val reach = maxPx * .25f
    private val busy get() = job?.isActive == true

    private fun rubber(x: Float) = reach * (1f - exp(-x / reach))
    private fun show() { collapsed = if (isCollapsed) maxPx - rubber(pull) else rubber(pull) }

    fun animateTo(t: Float, ms: Int = 420) {
        job?.cancel()
        job = scope.launch { animate(collapsed, t, animationSpec = tween(ms)) { v, _ -> collapsed = v } }
    }
    fun flip(to: Boolean) { isCollapsed = to; pull = 0f; animateTo(if (to) maxPx else 0f) }

    // d > 0: тянем в сторону сворачивания. Возвращает, сколько движения забрала шапка (остальное уйдёт списку)
    fun drag(d: Float): Float {
        if (busy) return d
        val away = if (isCollapsed) -d else d
        if (pull == 0f && away <= 0f) return 0f
        pull = (pull + away).coerceAtLeast(0f)
        if (pull >= threshold) { flip(!isCollapsed); return d }
        show()
        return d
    }

    // палец отпущен: быстрый бросок доводит переход, иначе шапка возвращается назад
    fun release(vy: Float) {
        if (busy) return
        val flick = pull > 0f && ((!isCollapsed && vy < -700f) || (isCollapsed && vy > 700f))
        if (flick) flip(!isCollapsed) else if (pull > 0f) { pull = 0f; animateTo(if (isCollapsed) maxPx else 0f, 260) }
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
    val hs = remember(u) { HeaderState(STORIES * u, scope) }
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

// шапка: заголовок, очередь «сторис» и вкладки; при сворачивании очередь улетает на место кнопки архива
@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, hs: HeaderState) {
    val u = LocalDensity.current.density
    val ss = rememberScrollState()
    val n = queueChats.size
    val showHit by remember(hs) { derivedStateOf { hs.p > .9f } }
    val canScroll by remember(hs) { derivedStateOf { hs.p < .3f } }
    val drag = rememberDraggableState { d -> hs.drag(-d) }
    val ease = { t: Float -> FastOutSlowInEasing.transform(t.coerceIn(0f, 1f)) }
    val stick = { t: Float -> StickEasing.transform(t.coerceIn(0f, 1f)) }
    BoxWithConstraints(Modifier.fillMaxWidth().background(Surface).draggable(drag, Orientation.Vertical, onDragStopped = { v -> hs.release(v) })) {
        Column {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { IconBox { VIcon(SEARCH, Primary, 28.dp, evenOdd = true) } }
                Row(Modifier.align(Alignment.Center), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                    T("Общение", ts(20, 28, 500, 0f, Color.Black))
                    Box(Modifier.width(1.dp).height(24.dp).background(Text1.copy(alpha = .14f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = Color(0xFFB6AEAA)
                        VIcon(TG, c, 24.dp, 20.dp, vx = 3.5f, vy = 6f, vw = 15f, vh = 13f)
                        T("ТГ", ts(20, 28, 500, 0f, c))
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().storiesHeight(hs))
            Tabs(tab, onTab)
        }

        // подписи: не режутся рамкой, а уезжают вверх вместе с аватарками и быстро гаснут
        Box(Modifier.offset(y = BAR.dp).fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
            queueChats.forEachIndexed { i, c ->
                Column(
                    Modifier.padding(top = 64.dp).width(64.dp).graphicsLayer {
                        val p = hs.p
                        val pi = (p * 1.5f - i * .08f).coerceIn(0f, 1f)
                        translationX = (16 + i * 72) * u - ss.value * (1 - p) - 4 * u
                        translationY = -50f * u * ease((pi * 1.35f).coerceAtMost(1f))
                        alpha = 1f - ease(p / .18f)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    T(c.name, ts(12, 16, 500, 0f, Text1))
                    T(c.time, ts(11, 16, 400, 0f, if (i < 2) Primary else Text2))
                }
            }
        }

        // аватары: стопка из первых трёх; остальные заезжают под третью и выезжают из-под неё.
        // Лёгкая «липкость»: в полёте аватарки чуть вытягиваются, у разворота слегка пружинят
        queueChats.forEachIndexed { i, c ->
            Box(
                Modifier
                    .graphicsLayer {
                        val p = hs.p
                        val pi = (p * 1.5f - i * .08f).coerceIn(0f, 1f)
                        val xe = stick(pi)
                        val ye = ease(pi * 1.35f)
                        val x0 = (16 + i * 72) * u - ss.value * (1 - ease(pi))
                        val x1 = (12 + minOf(i, 2) * 16) * u
                        translationX = x0 + (x1 - x0) * xe
                        translationY = ((BAR + 4) + ((BAR - 46) - (BAR + 4)) * ye) * u
                        val s = 1f - .5f * ease(pi)
                        val mid = sin(pi * 3.1416f)
                        scaleX = s * (1f + .10f * mid); scaleY = s * (1f - .06f * mid)
                        transformOrigin = TransformOrigin(0f, 0f)
                        // четвёртая и далее спрятаны под третьей; гаснут только в самом конце, чтобы не было каёмки
                        alpha = if (i < 3) 1f else 1f - ((pi - .85f) / .15f).coerceIn(0f, 1f)
                    }
                    .zIndex((n - i).toFloat())
                    .size(56.dp)
                    .drawBehind { drawCircle(Surface, size.minDimension / 2 + 3.dp.toPx()) }
            ) { Avatar(c.init, 56, 20, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2)))) }
        }

        if (canScroll) Box(Modifier.offset(y = BAR.dp).fillMaxWidth().storiesHeight(hs).horizontalScroll(ss)) {
            Spacer(Modifier.width((16 + n * 72 - 8 + 16).dp).height(1.dp))
        }

        // тап по стопке аватаров возвращает очередь
        if (showHit) Box(
            Modifier.size(84.dp, BAR.dp)
                .clickable(remember { MutableInteractionSource() }, null) { hs.flip(false) }
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
      Box(Modifier.padding(horizontal = 16.dp).height(48.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            names.forEachIndexed { i, n ->
                val on = i == sel
                val col by animateColorAsState(if (on) Primary else Text2, label = "c")
                Row(
                    Modifier.height(48.dp)
                        .onGloballyPositioned { xs[i] = it.positionInParent().x; ws[i] = it.size.width.toFloat() }
                        .clickable(remember { MutableInteractionSource() }, null) { onSel(i) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    T(n, ts(16, 24, 500, .15f, col))
                    if (i == 0) Box(
                        Modifier.defaultMinSize(20.dp).height(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Primary else Mute).padding(horizontal = 6.dp),
                        Alignment.Center
                    ) { T(chats.count { it.badge != null }.toString(), ts(12, 16, 500, 0f, if (on) Color.White else Surface)) }
                }
            }
        }
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.align(Alignment.BottomStart)
                .width(with(LocalDensity.current) { w.toDp() }).height(3.dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Primary)
        )
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
    Row(
        Modifier.fillMaxWidth().height(80.dp).background(if (pressed) Primary.copy(alpha = .09f) else Color.Transparent)
            .clickable(src, null) {}.padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.padding(top = 10.dp).size(60.dp)) {
            Avatar(c.init, 60, 20, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2))))
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(top = 9.dp, end = 16.dp)) {
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
                        T(c.time, ts(13, 22, 400, 0f, Text2))
                    }
                }
                Column(Modifier.padding(top = 2.dp, end = if (c.badge != null) 34.dp else 0.dp).height(40.dp)) {
                    if (c.sender != null) T(c.sender, ts(14, 20, 400, .25f, Text1))
                    T(c.text, ts(14, 20, 400, .25f, Text2), lines = if (c.sender != null) 1 else 2)
                }
            }
            if (c.badge != null) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 39.dp).defaultMinSize(22.dp).height(22.dp)
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
            Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFFB366), Color(0xFFF97B00)))), Alignment.Center) {
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
