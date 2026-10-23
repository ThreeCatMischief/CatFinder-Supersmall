package com.catfinder.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catfinder.app.R
import com.catfinder.app.data.AppConfig
import com.catfinder.app.data.DriveConfigForm
import com.catfinder.app.data.StickMode
import com.catfinder.app.data.VideoGovernor
import com.catfinder.app.drive.StraightCal
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.clearMessage()
        vm.loadDriveConfig()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.show(Screen.DRIVE) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp).widthIn(max = 520.dp),
                    maxLines = 2,
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.padding(top = 4.dp))

        Row(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectionSection(vm)
                DrivingSection(vm)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StraightSection(vm)
                WheelsSection(vm)
                AboutSection(vm)
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                subtitle?.let { Hint(it) }
            }
            content()
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ConnectionSection(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    Section("Connection", null) {
        OutlinedTextField(
            value = config.address,
            onValueChange = { vm.updateConfig(config.copy(address = it.trim())) },
            label = { Text("Robot address") },
            placeholder = { Text("192.168.1.50 or catfinder.local") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::findOnWifi) { Text("Find on my WiFi") }
            OutlinedButton(onClick = vm::testConnection) { Text("Test connection") }
        }
        Hint(
            "On the same WiFi as the robot: tap Find on my WiFi, or enter the address shown in your router. " +
                "Away from home: enter the robot's home IP address and turn Tailscale on (see the README).",
        )
        Text("Video quality")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(AppConfig.VIDEO_AUTO to "Auto") + VideoGovernor.LABELS.mapIndexed { i, l -> i to l }
            for ((level, label) in options) {
                FilterChip(
                    selected = config.videoLevel == level,
                    onClick = { vm.updateConfig(config.copy(videoLevel = level)) },
                    label = { Text(label) },
                )
            }
        }
        Hint("Auto lowers the picture quality when the connection gets slow, so steering stays quick.")
    }
}

@Composable
private fun DrivingSection(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    Section("Driving", "Stored on this phone.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.mode == StickMode.ARCADE,
                onClick = { vm.updateConfig(config.copy(mode = StickMode.ARCADE)) },
                label = { Text("One stick") },
            )
            FilterChip(
                selected = config.mode == StickMode.TANK,
                onClick = { vm.updateConfig(config.copy(mode = StickMode.TANK)) },
                label = { Text("Two sticks (tank)") },
            )
        }
        Column {
            Row {
                Text("Max speed", Modifier.weight(1f))
                Text("${config.maxSpeed} %", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = config.maxSpeed.toFloat(),
                onValueChange = { vm.updateConfig(config.copy(maxSpeed = it.roundToInt())) },
                valueRange = 10f..100f,
            )
        }
    }
}

@Composable
private fun StraightSection(vm: AppViewModel) {
    val cfg by vm.driveCfg.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Section("Drive straight", "No two servos are alike, so the tank may curve. Teach it to go straight.") {
        Hint(
            "Tap Start, then drive forward. Steer gently until CatFinder goes straight and keep the stick still " +
                "for ${StraightCal.HOLD_MS / 1000} seconds: that is saved for the speed you are driving. " +
                "Repeat at slow and fast speed.",
        )
        Button(onClick = vm::startStraightCal, enabled = cfg != null) { Text("Start") }
        val c = cfg
        if (c != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DriveConfigForm.BALANCE.forEachIndexed { i, (key, _) ->
                    val v = c[key]?.toIntOrNull() ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${StraightCal.POINTS[i]} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(if (v == 0) "–" else if (v > 0) "L −$v" else "R −${-v}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Hint("How much the left (L) or right (R) wheel is slowed at each speed, in µs.")
        } else {
            Hint("Connect to the robot to start.")
        }
        TextButton(onClick = { confirmClear = true }, enabled = c != null) { Text("Clear") }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear drive straight?",
            text = "Both wheels get the same signal again at every speed.",
            confirm = "Clear",
            onConfirm = vm::resetStraight,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun WheelsSection(vm: AppViewModel) {
    val cfg by vm.driveCfg.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    Section("Wheels", "Stored on the robot. Changes apply at once.") {
        val c = cfg
        if (c == null) {
            Hint("Not loaded – the robot did not answer yet.")
        } else {
            for ((key, label) in DriveConfigForm.FLAGS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(checked = c[key] == "1", onCheckedChange = { vm.setDriveFlag(key, it) })
                }
            }
        }
        Hint("If a tread creeps while standing still, adjust its stop point on the robot's web page under Setup → Wheels.")
        TextButton(onClick = { confirmReset = true }, enabled = c != null) { Text("Reset all wheel settings") }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset wheel settings?",
            text = "Puts all wheel settings stored on the robot back to the defaults, including drive straight.",
            confirm = "Reset",
            onConfirm = vm::resetDriveConfig,
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun AboutSection(vm: AppViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshStatus() }

    Section("About this robot", null) {
        val s = status
        if (s != null) {
            val rows = listOfNotNull(
                s.optString("version").takeIf { it.isNotEmpty() }?.let { "Software" to it },
                s.optString("ssid").takeIf { it.isNotEmpty() }?.let { "WiFi network" to it },
                s.optString("ip").takeIf { it.isNotEmpty() }?.let { "Address" to it },
                if (s.has("rssi")) "Signal" to signal(s.optInt("rssi")) else null,
            )
            for ((label, value) in rows) {
                Row {
                    Text(label, Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value)
                }
            }
        } else {
            Hint("The robot did not answer yet.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::refreshStatus) { Text("Refresh") }
            TextButton(onClick = {
                val file = vm.exportDiagLog()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share diagnostic log"))
            }) { Text("Share diagnostic log") }
        }
        Hint("The diagnostic log helps when reporting a problem.")
    }
}

private fun signal(rssi: Int): String = when {
    rssi >= -60 -> "good ($rssi dBm)"
    rssi >= -72 -> "fair ($rssi dBm)"
    else -> "weak ($rssi dBm)"
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
