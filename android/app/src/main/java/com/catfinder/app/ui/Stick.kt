package com.catfinder.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.catfinder.app.ui.theme.Amber
import com.catfinder.app.ui.theme.Navy
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Spring-loaded stick. Reports deflection -1..1 per axis (right and up positive)
 * while touched and (0, 0) on release. [vertical] gives a one-axis track, used
 * in tank mode; x is then always 0. The knob is a paw.
 */
@Composable
fun Stick(modifier: Modifier, vertical: Boolean = false, onMove: (x: Float, y: Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val move by rememberUpdatedState(onMove)

    Canvas(
        modifier.pointerInput(vertical) {
            val knobR = min(size.width, size.height) * if (vertical) 0.4f else 0.18f
            val travelX = size.width / 2f - knobR
            val travelY = size.height / 2f - knobR

            fun set(p: Offset) {
                var x = if (vertical) 0f else (p.x - size.width / 2f) / travelX
                var y = -(p.y - size.height / 2f) / travelY
                val len = sqrt(x * x + y * y)
                if (len > 1f) {
                    x /= len
                    y /= len
                }
                knob = Offset(x, y)
                move(x, y)
            }

            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                set(down.position)
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        set(change.position)
                    }
                } finally {
                    knob = Offset.Zero
                    move(0f, 0f)
                }
            }
        },
    ) {
        val knobR = min(size.width, size.height) * if (vertical) 0.4f else 0.18f
        val travelX = size.width / 2f - knobR
        val travelY = size.height / 2f - knobR
        val base = Color.White.copy(alpha = 0.18f)
        val rim = Color.White.copy(alpha = 0.45f)
        if (vertical) {
            val w = knobR * 2f
            val tl = Offset(center.x - w / 2f, 0f)
            drawRoundRect(base, tl, Size(w, size.height), CornerRadius(w / 2f))
            drawRoundRect(rim, tl, Size(w, size.height), CornerRadius(w / 2f), style = Stroke(2f))
        } else {
            val r = min(size.width, size.height) / 2f
            drawCircle(base, r)
            drawCircle(rim, r, style = Stroke(2f))
        }
        drawLine(rim, Offset(center.x, knobR), Offset(center.x, size.height - knobR), 1.5f)
        if (!vertical) drawLine(rim, Offset(knobR, center.y), Offset(size.width - knobR, center.y), 1.5f)
        val c = Offset(center.x + knob.x * travelX, center.y - knob.y * travelY)
        drawCircle(Amber.copy(alpha = if (knob == Offset.Zero) 0.6f else 0.95f), knobR, c)
        paw(c, knobR * 0.75f, Navy.copy(alpha = 0.85f))
    }
}

/** Paw print centred on [c], [r] roughly its radius. */
private fun DrawScope.paw(c: Offset, r: Float, color: Color) {
    drawOval(color, Offset(c.x - r * 0.45f, c.y - r * 0.05f), Size(r * 0.9f, r * 0.7f))
    for ((dx, dy) in listOf(-0.55f to -0.3f, -0.2f to -0.62f, 0.2f to -0.62f, 0.55f to -0.3f)) {
        drawCircle(color, r * 0.2f, Offset(c.x + dx * r, c.y + dy * r))
    }
}
