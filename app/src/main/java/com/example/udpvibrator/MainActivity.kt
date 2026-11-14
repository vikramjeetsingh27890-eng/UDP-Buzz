package com.example.udpvibrator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { AppTheme { MainScreen() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedByDescending { it.name.startsWith("wlan") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    var s by remember { mutableStateOf(store.load()) }
    var portText by remember { mutableStateOf(s.port.toString()) }
    val state by UdpService.state.collectAsState()
    val ip = remember(state.running) { localIpv4() ?: "unknown" }
    val hasAmplitude = remember { Haptics.hasAmplitudeControl(ctx) }

    fun update(n: VibeSettings) {
        s = n
        store.save(n)
    }

    val portValid = portText.toIntOrNull()?.let { it in 1024..65535 } == true

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("UDP Vibrator", style = MaterialTheme.typography.headlineMedium)

            // Status
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (state.running) "Listening" else "Stopped",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (state.running) "Send to $ip:${state.port}" else "Device IP: $ip",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (state.running) {
                        Text(
                            "Signals: ${state.received}" +
                                (state.lastFrom?.let { "  ·  last from $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Button(
                onClick = {
                    if (state.running) UdpService.stop(ctx) else UdpService.start(ctx)
                },
                enabled = state.running || portValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (state.running) "Stop" else "Start listening")
            }

            // Network settings
            Text("Network", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = portText,
                onValueChange = { v ->
                    portText = v.filter(Char::isDigit).take(5)
                    portText.toIntOrNull()?.takeIf { it in 1024..65535 }
                        ?.let { update(s.copy(port = it)) }
                },
                label = { Text("UDP port") },
                supportingText = { if (!portValid) Text("Use 1024–65535") },
                isError = !portValid,
                enabled = !state.running,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.allowedIp,
                onValueChange = { update(s.copy(allowedIp = it.trim())) },
                label = { Text("Only accept from IP (optional)") },
                placeholder = { Text("Any sender") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            // Vibration settings
            Text("Vibration", style = MaterialTheme.typography.titleMedium)
            LabeledSlider("Duration", "${s.durationMs} ms", s.durationMs, 20..2000, step = 10) {
                update(s.copy(durationMs = it))
            }
            LabeledSlider(
                "Strength",
                if (hasAmplitude) "${(s.strength * 100f / 255).roundToInt()}%" else "not supported",
                s.strength, 1..255, enabled = hasAmplitude
            ) { update(s.copy(strength = it)) }
            LabeledSlider("Pulses", "${s.repeats}", s.repeats, 1..10) {
                update(s.copy(repeats = it))
            }
            LabeledSlider(
                "Pause between pulses", "${s.gapMs} ms", s.gapMs, 0..1000,
                step = 10, enabled = s.repeats > 1
            ) { update(s.copy(gapMs = it)) }

            OutlinedButton(
                onClick = { Haptics.vibrate(ctx, s) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test vibration") }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    enabled: Boolean = true,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Column {
        Text("$label: $valueText", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onChange(((it / step).roundToInt() * step).coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled
        )
    }
}
