package com.roger.circuitcaller.ui.settings

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roger.circuitcaller.data.AppSettings
import com.roger.circuitcaller.ui.Eyebrow
import com.roger.circuitcaller.ui.RoundIconButton
import com.roger.circuitcaller.ui.SettingRow
import com.roger.circuitcaller.ui.ToggleRow
import com.roger.circuitcaller.ui.VSpace
import com.roger.circuitcaller.ui.theme.Line
import com.roger.circuitcaller.ui.theme.Surface2
import com.roger.circuitcaller.ui.theme.TextDim
import com.roger.circuitcaller.ui.theme.Work
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onVoice: (Boolean) -> Unit,
    onCountdown: (Boolean) -> Unit,
    onBeeps: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onHalfway: (Boolean) -> Unit,
    onVoiceName: (String) -> Unit,
    onRate: (Float) -> Unit,
    onPrep: (Int) -> Unit
) {
    val context = LocalContext.current
    var voiceNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewTts by remember { mutableStateOf<TextToSpeech?>(null) }

    // A short-lived engine just for the picker and the Test button; the service owns its own.
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                voiceNames = engine?.voices
                    ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
                previewTts = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            previewTts = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        VSpace(10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Default.ArrowBack, "Back", onBack, size = 40.dp)
            Text("  Voice & sound", style = MaterialTheme.typography.titleLarge)
        }
        VSpace(14)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                ToggleRow(
                    "Voice callouts",
                    "Speak exercise names",
                    settings.voiceEnabled,
                    onVoice
                )
            }
            item {
                ToggleRow(
                    "Spoken countdown",
                    "3 – 2 – 1 before each set",
                    settings.countdownEnabled,
                    onCountdown
                )
            }
            item {
                ToggleRow(
                    "Halfway callout",
                    "Say “halfway” during sets of a minute or more",
                    settings.halfwayCallout,
                    onHalfway
                )
            }
            item {
                ToggleRow("Beeps", "Tone on the last 3 seconds", settings.beepsEnabled, onBeeps)
            }
            item {
                ToggleRow(
                    "Vibrate",
                    "Buzz on each phase change",
                    settings.vibrateEnabled,
                    onVibrate
                )
            }
            item {
                ToggleRow(
                    "Keep screen on",
                    "The timer runs either way",
                    settings.keepScreenOn,
                    onKeepScreenOn
                )
            }

            item {
                SettingRow("Voice") {
                    VoiceDropdown(
                        selected = settings.ttsVoiceName,
                        options = voiceNames,
                        onPick = { name ->
                            onVoiceName(name)
                            previewTts?.voices?.firstOrNull { it.name == name }?.let {
                                previewTts?.voice = it
                            }
                        }
                    )
                }
            }

            item {
                SettingRow(
                    "Speech speed",
                    "%.2f×".format(settings.speechRate)
                ) {
                    Slider(
                        value = settings.speechRate,
                        onValueChange = onRate,
                        valueRange = 0.6f..1.6f,
                        steps = 19,
                        modifier = Modifier.width(140.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Work,
                            activeTrackColor = Work,
                            inactiveTrackColor = Surface2
                        )
                    )
                }
            }

            item {
                SettingRow("Prep time", "Countdown before each set") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 3, 5, 10).forEach { value ->
                            val selected = settings.prepSeconds == value
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Work.copy(alpha = 0.18f) else Surface2)
                                    .border(
                                        1.dp,
                                        if (selected) Work else Line,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onPrep(value) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    if (value == 0) "off" else "${value}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Work else TextDim
                                )
                            }
                        }
                    }
                }
            }

            item {
                VSpace(6)
                OutlinedButton(
                    onClick = {
                        previewTts?.setSpeechRate(settings.speechRate)
                        previewTts?.speak(
                            "Wall press",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "preview"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "TEST CALLOUT",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent("com.android.settings.TTS_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "ANDROID VOICE SETTINGS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                VSpace(6)
                Eyebrow("Install more voices from Android settings")
                VSpace(20)
            }
        }
    }
}

@Composable
private fun VoiceDropdown(selected: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selected.isBlank() -> "System default"
        selected.length > 22 -> selected.take(22) + "…"
        else -> selected
    }

    Box {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .border(1.dp, Line, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("System default") },
                onClick = {
                    onPick("")
                    expanded = false
                }
            )
            options.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onPick(name)
                        expanded = false
                    }
                )
            }
        }
    }
}
