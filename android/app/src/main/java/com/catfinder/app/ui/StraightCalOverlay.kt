package com.catfinder.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catfinder.app.drive.StraightCal
import com.catfinder.app.ui.theme.Amber
import com.catfinder.app.ui.theme.LinkBad
import com.catfinder.app.ui.theme.LinkOk
import kotlin.math.ceil

private fun ringColor(s: StraightCalState): Color = when (s.phase) {
    CalPhase.SAVED -> LinkOk
    CalPhase.FAILED -> LinkBad
    else -> Amber
}

/** Banner for drive-straight mode: what to do, a countdown ring and the speeds done. */
@Composable
fun StraightCalOverlay(s: StraightCalState, onDone: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(s.phase) {
        if (s.phase == CalPhase.SAVED || s.phase == CalPhase.FAILED) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val color by animateColorAsState(ringColor(s), label = "ring")
    val progress by animateFloatAsState(s.progress, tween(if (s.progress == 0f) 150 else 100), label = "progress")
    val speed = s.point?.let { "${StraightCal.POINTS[it]} %" }

    Column(
        modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val stroke = 6.dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.2f), radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
                    drawArc(
                        color, -90f, 360f * progress, useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                val centre = when (s.phase) {
                    CalPhase.HOLD -> ceil((1f - s.progress) * StraightCal.HOLD_MS / 1000f).toInt().coerceAtLeast(1).toString()
                    CalPhase.SAVED -> "✓"
                    CalPhase.FAILED -> "!"
                    else -> ""
                }
                Text(centre, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Drive straight", color = Color.White, style = MaterialTheme.typography.titleMedium)
                val text = when (s.phase) {
                    CalPhase.DRIVE -> if (speed == null) {
                        "Push the stick forward. Steer until CatFinder drives straight, then keep the stick still."
                    } else {
                        "Driving at about $speed. Once it drives straight, keep the stick still."
                    }
                    CalPhase.HOLD -> "Keep still… saving straight for $speed."
                    CalPhase.SAVING -> "Saving…"
                    CalPhase.SAVED -> "Saved for $speed. Let go of the stick, then try another speed or tap Done."
                    CalPhase.FAILED -> "${s.error}. Let go of the stick and try again."
                }
                Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDone) { Text("Done") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Speeds", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            StraightCal.POINTS.forEachIndexed { i, p ->
                val done = i in s.done
                val current = i == s.point
                Text(
                    if (done) "✓ $p %" else "$p %",
                    color = if (done) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (done) LinkOk else Color.White.copy(alpha = 0.12f))
                        .border(2.dp, if (current) Amber else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** A ring around the stick that fills while the stick is held still. */
@Composable
fun HoldRing(s: StraightCalState, modifier: Modifier) {
    val color by animateColorAsState(ringColor(s), label = "ring")
    val progress by animateFloatAsState(s.progress, tween(if (s.progress == 0f) 150 else 100), label = "progress")
    Canvas(modifier) {
        val stroke = 8.dp.toPx()
        drawArc(
            color, -90f, 360f * progress, useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f), size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
