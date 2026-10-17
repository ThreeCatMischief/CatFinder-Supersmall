package com.catfinder.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catfinder.app.R
import com.catfinder.app.data.StickMode
import com.catfinder.app.data.VideoGovernor
import com.catfinder.app.ui.theme.LinkBad
import com.catfinder.app.ui.theme.LinkOk

@Composable
fun DriveScreen(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val video by vm.video.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val sent by vm.sent.collectAsStateWithLifecycle()
    val rssi by vm.rssi.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val videoLevel by vm.videoLevel.collectAsStateWithLifecycle()
    val overlay = Color.Black.copy(alpha = 0.55f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val frame = video.frame
        if (frame != null) {
            Image(frame, contentDescription = "Camera", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (config.address.isBlank()) {
            if (searching) {
                NoVideo("Looking for CatFinder…", "Searching this WiFi.", blinking = true, Modifier.align(Alignment.Center))
            } else {
                NoVideo(
                    "No robot set up yet.", "Open Settings and tap Find on my WiFi.",
                    blinking = false, Modifier.align(Alignment.Center),
                )
            }
        } else {
            NoVideo(
                if (video.error == null) "Opening eyes…" else "Can't see a thing", video.error,
                blinking = video.error == null, Modifier.align(Alignment.Center),
            )
        }
        if (frame != null && video.error != null) {
            Text(
                "Video: ${video.error}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .clip(RoundedCornerShape(8.dp)).background(overlay).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Status chip
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp)).background(overlay).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dot = when (link.ok) {
                true -> LinkOk
                false -> LinkBad
                null -> Color.Gray
            }
            Spacer(Modifier.size(10.dp).clip(CircleShape).background(dot))
            val text = if (link.ok == false) {
                "${mood(sent, false)} – ${link.error ?: "no connection"}"
            } else {
                listOfNotNull(
                    mood(sent, link.ok),
                    link.rttMs?.let { "$it ms" },
                    "%.0f fps".format(video.fps),
                    video.lagMs?.let { "video lag $it ms" },
                    videoLevel?.let { VideoGovernor.LABELS.getOrNull(it) },
                    rssi?.let { "$it dBm" },
                    "L ${sent.left}  R ${sent.right}",
                ).joinToString("  ·  ")
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(
            onClick = { vm.show(Screen.SETTINGS) },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(overlay),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }

        when (config.mode) {
            StickMode.ARCADE -> Stick(
                Modifier.align(Alignment.BottomStart).padding(28.dp).size(190.dp),
            ) { x, y -> vm.setStick(x, y) }
            StickMode.TANK -> {
                Stick(
                    Modifier.align(Alignment.BottomStart).padding(28.dp).size(width = 90.dp, height = 220.dp),
                    vertical = true,
                ) { _, y -> vm.setTank(left = true, v = y) }
                Stick(
                    Modifier.align(Alignment.BottomEnd).padding(28.dp).size(width = 90.dp, height = 220.dp),
                    vertical = true,
                ) { _, y -> vm.setTank(left = false, v = y) }
            }
        }
    }
}

/** Logo with a slow blink while the camera is not delivering. */
@Composable
private fun NoVideo(title: String, detail: String?, blinking: Boolean, modifier: Modifier) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp).alpha(if (blinking) blink else 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (detail != null) {
            Text(detail, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
