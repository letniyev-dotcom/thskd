package ru.letniev.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

@Composable
fun ChatsScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val list = when (tab) { 1 -> chats.filter { it.badge != null }; 2 -> chats.filter { it.shared }; else -> chats }
    val ff = remember { loadFont(ctx) }
    CompositionLocalProvider(LocalFF provides ff) { Column(Modifier.fillMaxSize().background(Surface)) {
        Column(Modifier.statusBarsPadding()) { TopBar(); Tabs(tab) { tab = it } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list) { ChatRow(it) }
            if (tab == 0) item { Queue() }
            item { Spacer(Modifier.height(16.dp)) }
        }
        NavBar()
    } }
}

@Composable
private fun IconBox(content: @Composable () -> Unit) =
    Box(Modifier.size(48.dp), Alignment.Center) { content() }

@Composable
private fun TopBar() {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBox { VIcon(ARCHIVE, Primary, 28.dp, stroke = 1.5f) }
        Row(Modifier.weight(1f), Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), Alignment.CenterVertically) {
            T("Общение", ts(20, 28, 500, 0f, Color.Black))
            Box(Modifier.width(1.dp).height(24.dp).background(Text1.copy(alpha = .14f)))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = Color(0xFFB6AEAA)
                VIcon(TG, c, 24.dp, 20.dp, vx = 3.5f, vy = 6f, vw = 15f, vh = 13f)
                T("ТГ", ts(20, 28, 500, 0f, c))
            }
        }
        IconBox { VIcon(SEARCH, Primary, 28.dp, evenOdd = true) }
    }
}

@Composable
private fun Tabs(sel: Int, onSel: (Int) -> Unit) {
    val names = listOf("Все", "Непрочитанные", "Общие")
    val xs = remember { mutableStateListOf(0f, 0f, 0f) }
    val ws = remember { mutableStateListOf(0f, 0f, 0f) }
    val x by animateFloatAsState(xs[sel], tween(320), label = "x")
    val w by animateFloatAsState(ws[sel], tween(320), label = "w")
    Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp).fillMaxWidth().height(48.dp)) {
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
            if (c.online) Box(
                Modifier.align(Alignment.BottomEnd).offset(1.dp, 0.dp).size(17.dp).background(Color.White, CircleShape).padding(2.dp)
                    .background(Green, CircleShape)
            )
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
private fun Queue() {
    Column(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
        T("можно взять", ts(13, 18, 400, .1f, Text2), Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
        LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(queue) { q ->
                Column(Modifier.width(56.dp), Arrangement.spacedBy(4.dp), Alignment.CenterHorizontally) {
                    Avatar(q.init, 52, 18, q.photo, Brush.linearGradient(listOf(Color(q.color), Color(q.color))))
                    T(q.name, ts(11, 14, 500, .2f, Text1), align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun NavBar() {
    Row(Modifier.fillMaxWidth().background(NavBg).padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 8.dp).navigationBarsPadding()) {
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
