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
const val SEARCH = "M10.5 3.5a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM15.8 15.8L20.5 20.5"
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
