package ru.letniev.chats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.light(0, 0), SystemBarStyle.light(0, 0))
        setContent {
            var dark by rememberSaveable { mutableStateOf(false) }
            // иконки статус-бара и навигации следуют теме
            SideEffect {
                val s = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0)
                enableEdgeToEdge(s, s)
            }
            // макет рассчитан на экран 392dp: растягиваем все размеры пропорционально ширине,
            // системный масштаб шрифта не учитываем, чтобы было 1 к 1 как в html
            val d = LocalDensity.current
            val k = LocalConfiguration.current.screenWidthDp / 392f
            CompositionLocalProvider(LocalDensity provides Density(d.density * k, 1f)) {
                ChatsScreen(dark) { dark = !dark }
            }
        }
    }
}
