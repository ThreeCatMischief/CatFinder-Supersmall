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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catfinder.app.R
import com.catfinder.app.data.AppConfig
import com.catfinder.app.data.DriveConfigForm
import com.catfinder.app.data.StickMode
import com.catfinder.app.data.VideoGovernor
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
                JoystickSection(vm)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DeviceSection(vm)
                StatusSection(vm)
                DiagnosticsSection(vm)
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun ConnectionSection(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    Section("Connection", "How to reach the hunter.") {
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
        Text(
            "On the same WiFi as the robot: tap Find on my WiFi, or enter the address shown in your router. " +
                "Away from home: enter the robot's home IP address and turn Tailscale on (see the README).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledSlider("Send rate", "${config.sendRateHz} Hz", config.sendRateHz, 5..20) {
            vm.updateConfig(config.copy(sendRateHz = it))
        }
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
        Text(
            "Auto lowers resolution and frame rate when drive commands slow down, and raises them again after 8 s without delay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JoystickSection(vm: AppViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    Section("Handling", "How the stick feels. Stored on this phone.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.mode == StickMode.ARCADE,
                onClick = { vm.updateConfig(config.copy(mode = StickMode.ARCADE)) },
                label = { Text("One stick") },
            )
            FilterChip(
                selected = config.mode == StickMode.TANK,
                onClick = { vm.updateConfig(config.copy(mode = StickMode.TANK)) },
                label = { Text("Tank (two sticks)") },
            )
        }
        LabeledSlider("Max speed", "${config.maxSpeed} %", config.maxSpeed, 10..100) {
            vm.updateConfig(config.copy(maxSpeed = it))
        }
        LabeledSlider("Dead zone", "${config.deadZonePct} %", config.deadZonePct, 0..30) {
            vm.updateConfig(config.copy(deadZonePct = it))
        }
        LabeledSlider("Expo", "${config.expoPct} %", config.expoPct, 0..100) {
            vm.updateConfig(config.copy(expoPct = it))
        }
    }
}

@Composable
private fun DeviceSection(vm: AppViewModel) {
    val loaded by vm.driveCfg.collectAsStateWithLifecycle()
    val form = remember { mutableStateMapOf<String, String>() }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        form.clear()
        loaded?.let { form.putAll(it) }
    }

    Section("Drivetrain", "Stored on the robot itself.") {
        if (loaded == null) {
            Text("Not loaded – the robot did not answer yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            for ((key, label) in DriveConfigForm.FLAGS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f))
                    Switch(checked = form[key] == "1", onCheckedChange = { form[key] = if (it) "1" else "0" })
                }
            }
            NumberGrid(DriveConfigForm.NUMBERS, form)
            Text(
                "Balance: µs taken off the faster wheel; > 0 slows the left wheel, < 0 the right.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberGrid(DriveConfigForm.BALANCE, form, signed = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveDriveConfig(form.toMap()) }, enabled = loaded != null) { Text("Save") }
            OutlinedButton(onClick = vm::loadDriveConfig) { Text("Reload") }
            TextButton(onClick = { confirmReset = true }) { Text("Reset to defaults") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Forget the training?") },
            text = { Text("Overwrites the drive settings stored on the robot with the firmware defaults, including the balance calibration.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetDriveConfig()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberGrid(fields: List<Pair<String, String>>, form: MutableMap<String, String>, signed: Boolean = false) {
    for (pair in fields.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in pair) {
                OutlinedTextField(
                    value = form[key] ?: "",
                    onValueChange = { v ->
                        if (v.isEmpty() || v.matches(Regex(if (signed) "-?\\d{0,5}" else "\\d{0,5}"))) form[key] = v
                    },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusSection(vm: AppViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshStatus() }

    Section("Vital signs", "Straight from /status and /log.") {
        OutlinedButton(onClick = vm::refreshStatus) { Text("Refresh") }
        status?.let { s ->
            val rows = buildList<Pair<String, String>> {
                if (s.has("version")) add("Version" to "${s.opt("version")}")
                if (s.has("ssid")) add("WiFi network" to "${s.opt("ssid")}")
                for (key in s.keys()) if (key != "version" && key != "ssid") add(key to "${s.opt(key)}")
            }
            for ((label, value) in rows) {
                Row {
                    Text(label, Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (log.isNotEmpty()) {
            Text("Log", style = MaterialTheme.typography.titleSmall)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp),
            ) {
                Text(log, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(vm: AppViewModel) {
    val lines by vm.diagLog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Section("Diagnostics", "Every drive request, status poll and video second, stored on this phone.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val file = vm.exportDiagLog()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share diagnostic log"))
            }) { Text("Share log") }
            OutlinedButton(onClick = vm::clearDiagLog) { Text("Clear") }
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp),
        ) {
            Text(lines.takeLast(60).asReversed().joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, current: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}
