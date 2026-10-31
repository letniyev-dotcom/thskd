package ru.letniev.chats

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFFF97B00)
val Surface = Color(0xFFFDFBFF)
val NavBg = Color(0xFFF8F5FC)
val Text1 = Color(0xFF1F1C19)
val Text2 = Color(0xFF7B6F65)
val Mute = Color(0xFFC6B7A9)
val Green = Color(0xFF2BC016)

class Chat(
    val name: String, val init: String, val g1: Long, val g2: Long, val text: String, val time: String,
    val photo: String, val sender: String? = null, val badge: String? = null, val orange: Boolean = false,
    val online: Boolean = false, val muted: Boolean = false, val check: Boolean = false, val shared: Boolean = false
)

val chats = listOf(
    Chat("Марина", "М", 0xFFFF8A65, 0xFFC2185B, "Договорились, завтра в 18:00 созвонимся", "9:41", "https://randomuser.me/api/portraits/women/44.jpg", check = true),
    Chat("Алексей", "А", 0xFF7986CB, 0xFF3F2B96, "Скинул тебе файлы на почту, глянь, пожалуйста", "9:12", "https://randomuser.me/api/portraits/men/32.jpg", badge = "2", orange = true, online = true),
    Chat("Соседи по дому", "С", 0xFFFFB74D, 0xFFE64A19, "Кто-нибудь знает, когда починят лифт?", "8:56", "https://picsum.photos/seed/house/200/200", sender = "Игорь", badge = "5", muted = true, shared = true),
    Chat("Мама", "М", 0xFF90A4AE, 0xFF37474F, "Не забудь купить хлеб по дороге домой", "Ср", "https://randomuser.me/api/portraits/women/65.jpg", badge = "1", orange = true),
    Chat("Ольга Власова", "О", 0xFF4DB6AC, 0xFF00695C, "Спасибо большое, всё получилось!", "Вт", "https://randomuser.me/api/portraits/women/12.jpg", check = true)
)

class Waiting(val name: String, val init: String, val color: Long, val photo: String)

val queue = listOf(
    Waiting("Полина", "П", 0xFFF06292, "https://randomuser.me/api/portraits/women/26.jpg"),
    Waiting("Катя", "К", 0xFFBA68C8, "https://randomuser.me/api/portraits/women/68.jpg"),
    Waiting("Максим", "М", 0xFFFFB74D, "https://randomuser.me/api/portraits/men/46.jpg"),
    Waiting("Егор", "Е", 0xFF64B5F6, "https://randomuser.me/api/portraits/men/22.jpg"),
    Waiting("Аня", "А", 0xFF4DB6AC, "https://randomuser.me/api/portraits/women/90.jpg"),
    Waiting("Дима", "Д", 0xFF9575CD, "https://randomuser.me/api/portraits/men/55.jpg")
)
