package com.roger.circuitcaller.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roger.circuitcaller.timer.RunState
import com.roger.circuitcaller.timer.SegmentType
import com.roger.circuitcaller.timer.formatClock
import com.roger.circuitcaller.ui.HSpace
import com.roger.circuitcaller.ui.ProgressRing
import com.roger.circuitcaller.ui.RoundIconButton
import com.roger.circuitcaller.ui.SegmentBar
import com.roger.circuitcaller.ui.VSpace
import com.roger.circuitcaller.ui.colorForSegment
import com.roger.circuitcaller.ui.theme.Danger
import com.roger.circuitcaller.ui.theme.Line
import com.roger.circuitcaller.ui.theme.Surface
import com.roger.circuitcaller.ui.theme.TextDim
import com.roger.circuitcaller.ui.theme.TextPrimary
import com.roger.circuitcaller.ui.theme.Work

@Composable
fun RunScreen(
    state: RunState,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit
) {
    val segment = state.current ?: return
    val accent = colorForSegment(segment.type)
    var confirmStop by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpace(14)
        Text(
            "ROUND ${segment.round} OF ${state.totalRounds}",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
        VSpace(8)
        SegmentBar(
            segments = state.segments,
            currentIndex = state.index,
            currentFractionRemaining = state.segmentFraction
        )
        VSpace(18)

        ProgressRing(
            fractionRemaining = state.segmentFraction,
            color = accent,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 26.dp)
            ) {
                Text(
                    when (segment.type) {
                        SegmentType.PREP -> "GET READY"
                        SegmentType.WORK -> "WORK"
                        SegmentType.REST -> "REST"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (segment.type == SegmentType.PREP) TextDim else accent
                )
                VSpace(4)
                Text(
                    formatClock(state.remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary
                )
                if (segment.type != SegmentType.REST) {
                    VSpace(4)
                    Text(
                        segment.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
                if (state.paused) {
                    VSpace(6)
                    Text(
                        "PAUSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Work
                    )
                }
            }
        }

        VSpace(10)
        Text(
            state.nextWorkLabel?.let { "Up next: $it" } ?: "Last one — finish strong",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim
        )

        Box(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RoundIconButton(
                Icons.Default.KeyboardArrowLeft, "Previous", onPrevious, size = 54.dp
            )
            HSpace(18)
            RoundIconButton(
                icon = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Refresh,
                contentDescription = if (state.paused) "Resume" else "Pause",
                onClick = onPauseResume,
                size = 78.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
                background = Work,
                border = false
            )
            HSpace(18)
            RoundIconButton(
                Icons.Default.KeyboardArrowRight, "Skip", onSkip, size = 54.dp
            )
        }

        VSpace(14)
        Text(
            "${formatClock(state.workoutRemainingSeconds)} LEFT IN WORKOUT",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
        VSpace(10)
        RoundIconButton(
            Icons.Default.Close, "End workout", { confirmStop = true },
            size = 44.dp, tint = Danger
        )
        VSpace(18)
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("End this workout?") },
            text = { Text("Progress for this session won't be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    onStop()
                }) { Text("End", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("Keep going") }
            },
            containerColor = Surface
        )
    }
}

@Composable
fun DoneScreen(
    state: RunState,
    onAgain: () -> Unit,
    onHome: () -> Unit
) {
    val intervals = state.segments.count { it.type == SegmentType.WORK }
    val workSeconds = state.segments
        .filter { it.type == SegmentType.WORK }
        .sumOf { it.durationSeconds }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Workout complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        VSpace(6)
        Text(
            state.workoutName,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        VSpace(4)
        Text(
            "$intervals intervals · ${formatClock(workSeconds)} of work · " +
                "${formatClock(state.elapsedTotalSeconds)} elapsed",
            style = MaterialTheme.typography.labelMedium,
            color = TextDim,
            textAlign = TextAlign.Center
        )
        VSpace(30)
        Button(
            onClick = onAgain,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Work,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                "DO IT AGAIN",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        VSpace(10)
        OutlinedButton(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "BACK TO MY WORKOUTS",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}
