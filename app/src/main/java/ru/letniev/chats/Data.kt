package ru.letniev.chats

class Chat(
    val name: String, val init: String, val g1: Long, val g2: Long, val text: String, val time: String,
    val photo: String, val sender: String? = null, val badge: String? = null, val orange: Boolean = false,
    val online: Boolean = false, val muted: Boolean = false, val check: Boolean = false, val shared: Boolean = false
)

val chats = listOf(
    Chat("Марина", "М", 0xFFFF8A65, 0xFFC2185B, "Договорились, завтра в 18:00 созвонимся", "9:41", "https://randomuser.me/api/portraits/women/44.jpg", check = true),
    Chat("Алексей", "А", 0xFF7986CB, 0xFF3F2B96, "Скинул тебе файлы на почту, глянь, пожалуйста", "9:12", "https://randomuser.me/api/portraits/men/32.jpg", badge = "2", orange = true, online = true),
    Chat("Соседи по дому", "С", 0xFFFFB74D, 0xFFE64A19, "Кто-нибудь знает, когда починят лифт?", "8:56", "https://picsum.photos/seed/house/200/200", sender = "Игорь", badge = "5", muted = true, shared = true)
)

// чаты из «можно взять»: как обычные, но вместо времени сообщения время ожидания
val queueChats = listOf(
    Chat("Полина", "П", 0xFFF06292, 0xFFF06292, "Здравствуйте, можно задать вопрос?", "21 мин", "https://randomuser.me/api/portraits/women/26.jpg", badge = "7", orange = true),
    Chat("Катя", "К", 0xFFBA68C8, 0xFFBA68C8, "Подскажите, пожалуйста, по заказу", "14 мин", "https://randomuser.me/api/portraits/women/68.jpg", badge = "4", orange = true),
    Chat("Максим", "М", 0xFFFFB74D, 0xFFFFB74D, "Когда можно будет забрать?", "9 мин", "https://randomuser.me/api/portraits/men/46.jpg", badge = "1", orange = true),
    Chat("Егор", "Е", 0xFF64B5F6, 0xFF64B5F6, "Спасибо, жду ответа", "6 мин", "https://randomuser.me/api/portraits/men/22.jpg", badge = "2", orange = true),
    Chat("Аня", "А", 0xFF4DB6AC, 0xFF4DB6AC, "Добрый день!", "4 мин", "https://randomuser.me/api/portraits/women/90.jpg", badge = "3", orange = true),
    Chat("Дима", "Д", 0xFF9575CD, 0xFF9575CD, "Привет, есть минутка?", "2 мин", "https://randomuser.me/api/portraits/men/55.jpg", badge = "1", orange = true)
)
