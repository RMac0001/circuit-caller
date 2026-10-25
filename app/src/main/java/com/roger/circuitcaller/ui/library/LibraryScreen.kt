package com.roger.circuitcaller.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roger.circuitcaller.data.AppSettings
import com.roger.circuitcaller.data.WorkoutWithExercises
import com.roger.circuitcaller.timer.SegmentBuilder
import com.roger.circuitcaller.timer.formatClock
import com.roger.circuitcaller.ui.CardSurface
import com.roger.circuitcaller.ui.Eyebrow
import com.roger.circuitcaller.ui.VSpace
import com.roger.circuitcaller.ui.theme.Line
import com.roger.circuitcaller.ui.theme.Surface
import com.roger.circuitcaller.ui.theme.Surface2
import com.roger.circuitcaller.ui.theme.TextDim
import com.roger.circuitcaller.ui.theme.Work
import com.roger.circuitcaller.update.Release
import com.roger.circuitcaller.update.ReleaseInfo
import com.roger.circuitcaller.update.UpdatePhase
import com.roger.circuitcaller.update.UpdateState

// FlowRow lets the footer links wrap on narrow phones.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    workouts: List<WorkoutWithExercises>,
    settings: AppSettings,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    update: UpdateState,
    versionName: String,
    onCheckForUpdates: () -> Unit,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        VSpace(12)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Circuit Caller")
                Text("My workouts", style = MaterialTheme.typography.headlineMedium)
            }
        }
        VSpace(14)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            update.available?.let { release ->
                item(key = "update") {
                    UpdateCard(release, update, onUpdate = onUpdate, onDismiss = onDismissUpdate)
                }
            }
            items(workouts, key = { it.workout.id }) { w ->
                WorkoutCard(
                    workout = w,
                    prepSeconds = settings.prepSeconds,
                    onOpen = { onOpen(w.workout.id) },
                    onStart = { onStart(w.workout.id) }
                )
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .clickable { onNew() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = TextDim)
                        Text(
                            "  NEW WORKOUT",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDim
                        )
                    }
                }
            }
            item { VSpace(8) }
        }

        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            FooterLink("Voice settings", onSettings)
            FooterLink("Export", onExport)
            FooterLink("Import", onImport)
            FooterLink("Check for updates", onCheckForUpdates)
        }
        Text(
            "v$versionName",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun UpdateCard(release: ReleaseInfo, state: UpdateState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val busy = state.phase != UpdatePhase.IDLE
    val notes = remember(release.notes) { Release.notesPreview(release.notes) }

    CardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow("Update")
            Text("Version ${release.version} available", style = MaterialTheme.typography.titleLarge)
            if (notes.isNotEmpty()) {
                VSpace(6)
                Text(
                    notes,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            VSpace(12)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Not now", style = MaterialTheme.typography.labelMedium, color = TextDim)
                }
                Button(
                    onClick = onUpdate,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Work,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Surface2,
                        disabledContentColor = TextDim
                    )
                ) {
                    Text(
                        when (state.phase) {
                            UpdatePhase.IDLE -> "UPDATE"
                            UpdatePhase.DOWNLOADING -> state.percent?.let { "DOWNLOADING $it%" } ?: "DOWNLOADING"
                            UpdatePhase.INSTALLING -> "INSTALLING"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = TextDim,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun WorkoutCard(workout: WorkoutWithExercises, prepSeconds: Int, onOpen: () -> Unit, onStart: () -> Unit) {
    val count = workout.usableExercises.size
    val duration = SegmentBuilder.totalSeconds(workout, prepSeconds)

    CardSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onOpen() }
            ) {
                Text(
                    workout.workout.name.ifBlank { "Untitled workout" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$count exercise${if (count == 1) "" else "s"} · " +
                        "${workout.workout.rounds} round${if (workout.workout.rounds == 1) "" else "s"} · " +
                        formatClock(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (count > 0) Work else Surface)
                    .clickable(enabled = count > 0) { onStart() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start workout",
                    tint = if (count > 0) MaterialTheme.colorScheme.onPrimary else TextDim
                )
            }
        }
    }
}
