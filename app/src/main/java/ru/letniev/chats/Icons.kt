package ru.letniev.chats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val ARCHIVE = "M4.5 4h15A1.5 1.5 0 0 1 21 5.5V7a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 7V5.5A1.5 1.5 0 0 1 4.5 4ZM5 8.5V17a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3V8.5M10 12.5h4"
const val SEARCH = "M11.5 2.75C6.66751 2.75 2.75 6.66751 2.75 11.5C2.75 16.3325 6.66751 20.25 11.5 20.25C16.3325 20.25 20.25 16.3325 20.25 11.5C20.25 6.66751 16.3325 2.75 11.5 2.75ZM1.25 11.5C1.25 5.83908 5.83908 1.25 11.5 1.25C17.1609 1.25 21.75 5.83908 21.75 11.5C21.75 14.0605 20.8111 16.4017 19.2589 18.1982L22.5303 21.4697C22.8232 21.7626 22.8232 22.2374 22.5303 22.5303C22.2374 22.8232 21.7626 22.8232 21.4697 22.5303L18.1982 19.2589C16.4017 20.8111 14.0605 21.75 11.5 21.75C5.83908 21.75 1.25 17.1609 1.25 11.5Z"
const val TG = "M5.43 11.87c3.5-1.52 5.83-2.53 7-3.02 3.33-1.39 4.02-1.63 4.47-1.64.1 0 .32.02.46.14.12.1.15.23.17.33.02.1.04.31.02.48-.18 1.9-.96 6.5-1.36 8.62-.17.9-.5 1.2-.82 1.23-.7.06-1.23-.46-1.9-.9-1.06-.7-1.65-1.13-2.68-1.8-1.19-.78-.42-1.2.26-1.9.18-.18 3.25-2.98 3.31-3.23.01-.03.01-.15-.06-.21-.07-.06-.17-.04-.25-.02-.1.02-1.8 1.14-5.08 3.35-.48.33-.92.5-1.31.49-.43-.01-1.26-.24-1.88-.44-.76-.25-1.36-.38-1.31-.8.03-.22.33-.45.9-.69z"
const val CHECK = "M18 7l-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12-12-1.42-1.41zM.41 13.41L6 19l1.41-1.41L1.83 12 .41 13.41z"
const val BELL_O = "M12 2a6.500 6.500 0 0 0-6.500 6.500v3.200L3.700 15.400A1 1 0 0 0 4.600 17h14.800a1 1 0 0 0 .9-1.600l-1.800-3.700V8.500A6.500 6.500 0 0 0 12 2zm-4.500 6.500a4.500 4.500 0 0 1 9 0v3.700l.1.300 1.100 2H6.300l1.100-2 .1-.3zM9.500 19h5a2.500 2.500 0 0 1-5 0z"
const val BELL_F = "M12 2a6.5 6.5 0 0 0-6.5 6.5v3.2L3.7 15.4A1 1 0 0 0 4.600 17h14.800a1 1 0 0 0 .9-1.600l-1.800-3.700V8.500A6.500 6.500 0 0 0 12 2zM9.500 19a2.500 2.500 0 0 0 5 0z"
const val CHAT = "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12c0 1.6.376 3.112 1.043 4.453.178.356.237.763.134 1.148l-.595 2.226a1.3 1.3 0 0 0 1.591 1.592l2.226-.596a1.63 1.63 0 0 1 1.149.133A9.96 9.96 0 0 0 12 22Z"
const val SPEAKER = "M4 9.5v5h4l5 4v-13l-5 4z"
const val CROSS = "M15.5 9l5 6M20.5 9l-5 6"

@Composable
fun VIcon(
    d: String, color: Color, w: Dp = 24.dp, h: Dp = w, evenOdd: Boolean = false, stroke: Float = 0f,
    vx: Float = 0f, vy: Float = 0f, vw: Float = 24f, vh: Float = 24f, inner: Float = 1f
) {
    val path = remember(d, evenOdd) {
        PathParser().parsePathString(d).toPath().apply {
            fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        }
    }
    Canvas(Modifier.size(w, h)) {
        val s = minOf(size.width / vw, size.height / vh)
        withTransform({
            translate((size.width - vw * s) / 2, (size.height - vh * s) / 2)
            scale(s, s, Offset.Zero)
            translate(-vx, -vy)
            if (inner != 1f) scale(inner, inner, Offset(12f, 12f))
        }) {
            drawPath(path, color, style = if (stroke > 0f) Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill)
        }
    }
}
