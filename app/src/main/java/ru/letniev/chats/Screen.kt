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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
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

private const val STORIES = 104  // высота ряда очереди, dp

// ───────── резинка вместо андроидовского оверскролла ─────────
// Тянешь за край списка: он идёт за пальцем, но с нарастающим сопротивлением (чем дальше, тем туже).
// Отпустил: плавно возвращается без отскока. Так же небольшой толчок, если список долетел до края с разгона.
@Stable
private class Rubber(private val scope: CoroutineScope, u: Float) : NestedScrollConnection {
    private var raw by mutableFloatStateOf(0f)   // сколько реально протянул палец, px
    private var job: Job? = null
    private val limit = 120f * u                 // дальше этого списка не уехать никогда
    private val grip = .6f                       // «вязкость» в самом начале: 1 = без сопротивления

    val offset: Float get() = if (raw == 0f) 0f else sign(raw) * limit * (1f - exp(-abs(raw) * grip / limit))

    private fun springBack(v: Float = 0f, from: Float = raw) {
        job?.cancel()
        job = scope.launch { animate(from, 0f, v, spring(1f, 200f)) { x, _ -> raw = x } }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || raw == 0f) return Offset.Zero
        job?.cancel()
        val n = raw + available.y
        return if (raw * n <= 0f) { // палец вернулся за исходную точку: забираем ровно столько, сколько нужно до нуля
            val c = -raw; raw = 0f; Offset(0f, c)
        } else { raw = n; Offset(0f, available.y) }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        job?.cancel()
        raw += available.y
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (raw == 0f) return Velocity.Zero
        springBack()
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y != 0f && raw == 0f) springBack(available.y * .3f, 0f)
        return Velocity.Zero
    }
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
        ) {
            Column(Modifier.statusBarsPadding()) { Header(tab, { tab = it }) { toggle() } }
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

// шапка: архив слева, заголовок по центру, поиск справа, ряд очереди и вкладки; ничего не сворачивается
@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, onArchive: () -> Unit) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp)) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { IconBox(onArchive) { VIcon(ARCHIVE, pal.headerGray, 28.dp, stroke = 1.7f) } }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { IconBox { VIcon(SEARCH, pal.primary, 28.dp, stroke = 2f) } }
            Box(Modifier.align(Alignment.Center)) { T("Общение", ts(20, 28, 500, 0f, pal.title)) }
        }
        Stories()
        Tabs(tab, onTab)
    }
}

// очередь «можно взять»: аватарка, имя, время ожидания
@Composable
private fun Stories() {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(STORIES.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        queueChats.forEachIndexed { i, c ->
            Column(Modifier.width(64.dp).padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(c.init, 56, 20, c.photo, Brush.linearGradient(listOf(Color(c.g1), Color(c.g2))))
                Spacer(Modifier.height(6.dp))
                T(c.name, ts(13, 16, 500, 0f, pal.text1))
                T(c.time, ts(12, 16, 500, 0f, if (i < 2) pal.primary else pal.text2))
            }
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
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
      Box(Modifier.padding(horizontal = 10.dp).height(48.dp)) {
        // пилюля выбранной папки: едет под текстом от вкладки к вкладке
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), 0) }.align(Alignment.CenterStart)
                .width(with(LocalDensity.current) { w.toDp() }).height(34.dp)
                .clip(RoundedCornerShape(17.dp)).background(pal.primary.copy(alpha = .13f))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            names.forEachIndexed { i, n ->
                val on = i == sel
                val col by animateColorAsState(if (on) pal.primary else pal.text2, label = "c")
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
                            .background(if (on) pal.primary else pal.mute).padding(horizontal = 6.dp),
                        Alignment.Center
                    ) { T(chats.count { it.badge != null }.toString(), ts(12, 16, 500, 0f, if (on) pal.onPrimary else pal.surface)) }
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
    // как в iOS: аватарка 62, текст с 80dp, тонкий разделитель от начала текста до правого края
    Row(
        Modifier.fillMaxWidth().height(78.dp).background(if (pressed) pal.primary.copy(alpha = .09f) else Color.Transparent)
            .drawBehind { drawLine(pal.divider, Offset(80.dp.toPx(), size.height), Offset(size.width, size.height), strokeWidth = .5.dp.toPx()) }
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
                Modifier.align(Alignment.TopEnd).padding(top = 35.dp).defaultMinSize(22.dp).height(22.dp)
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
