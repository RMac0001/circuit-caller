package com.roger.circuitcaller.ui.editor

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roger.circuitcaller.data.Exercise
import com.roger.circuitcaller.data.WorkoutWithExercises
import com.roger.circuitcaller.timer.SegmentBuilder
import com.roger.circuitcaller.timer.formatClock
import com.roger.circuitcaller.ui.CardSurface
import com.roger.circuitcaller.ui.Eyebrow
import com.roger.circuitcaller.ui.RoundIconButton
import com.roger.circuitcaller.ui.StepperBox
import com.roger.circuitcaller.ui.VSpace
import com.roger.circuitcaller.ui.theme.Danger
import com.roger.circuitcaller.ui.theme.Line
import com.roger.circuitcaller.ui.theme.Rest
import com.roger.circuitcaller.ui.theme.Surface
import com.roger.circuitcaller.ui.theme.Surface2
import com.roger.circuitcaller.ui.theme.TextDim
import com.roger.circuitcaller.ui.theme.TextPrimary
import com.roger.circuitcaller.ui.theme.Work

/** Durations people actually pick, one tap away. */
private val WORK_PRESETS = listOf(15, 20, 30, 45, 60, 90, 120, 180)
private val REST_PRESETS = listOf(0, 10, 15, 20, 30, 45, 60, 90)

@Composable
fun EditorScreen(
    workout: WorkoutWithExercises,
    globalPrepSeconds: Int,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRounds: (Int) -> Unit,
    onDefaultRest: (Int) -> Unit,
    onAddExercise: () -> Unit,
    onUpdateExercise: (Exercise) -> Unit,
    onDeleteExercise: (Exercise) -> Unit,
    onMoveExercise: (Int, Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val exercises = workout.orderedExercises
    val usableCount = workout.usableExercises.size
    val total = SegmentBuilder.totalSeconds(workout, globalPrepSeconds)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        VSpace(10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                Icons.Default.ArrowBack,
                "Back",
                onBack,
                size = 40.dp
            )
            Text(
                "  Edit workout",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            RoundIconButton(Icons.Default.Add, "Duplicate workout", onDuplicate, size = 40.dp)
            Box(Modifier.width(8.dp))
            RoundIconButton(
                Icons.Default.Delete,
                "Delete workout",
                { confirmDelete = true },
                size = 40.dp,
                tint = Danger
            )
        }
        VSpace(14)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LiveTextField(
                    value = workout.workout.name,
                    identity = workout.workout.id,
                    onChange = onRename,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Workout name") },
                    capitalization = KeyboardCapitalization.Sentences
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperBox(
                        label = "Rounds",
                        value = workout.workout.rounds.toString(),
                        onDecrement = { onRounds((workout.workout.rounds - 1).coerceAtLeast(1)) },
                        onIncrement = { onRounds((workout.workout.rounds + 1).coerceAtMost(30)) },
                        modifier = Modifier.weight(1f)
                    )
                    StepperBox(
                        label = "Rest for new",
                        value = "${workout.workout.defaultRestSeconds}s",
                        onDecrement = {
                            onDefaultRest((workout.workout.defaultRestSeconds - 5).coerceAtLeast(0))
                        },
                        onIncrement = {
                            onDefaultRest((workout.workout.defaultRestSeconds + 5).coerceAtMost(600))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Eyebrow("Exercises")
                    Eyebrow(formatClock(total) + " total")
                }
            }

            items(exercises, key = { it.id }) { exercise ->
                val index = exercises.indexOfFirst { it.id == exercise.id }
                ExerciseRow(
                    exercise = exercise,
                    canMoveUp = index > 0,
                    canMoveDown = index < exercises.lastIndex,
                    onChange = onUpdateExercise,
                    onDelete = { onDeleteExercise(exercise) },
                    onMoveUp = { onMoveExercise(index, -1) },
                    onMoveDown = { onMoveExercise(index, 1) }
                )
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .clickable { onAddExercise() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ ADD EXERCISE",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
            }
            item { VSpace(4) }
        }

        Button(
            onClick = onStart,
            enabled = usableCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Work,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = Surface2,
                disabledContentColor = TextDim
            )
        ) {
            Text(
                if (usableCount > 0) "START WORKOUT" else "NAME AN EXERCISE FIRST",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("${workout.workout.name.ifBlank { "This workout" }} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
            containerColor = Surface
        )
    }
}

/**
 * A text field that types against local state instead of the database.
 *
 * Binding `value` straight to a Room-backed flow puts a round trip in the typing path:
 * write, query, re-emit, recompose. The field gets repainted from stale data before the
 * write lands, which resets the cursor to the start and makes each new character land on
 * top of the previous one. Holding the text locally keeps typing instant; [onChange]
 * still persists every edit, so nothing is lost.
 *
 * [identity] is the row this field belongs to. Changing it — a different workout, a
 * different exercise — reloads the field from [value].
 */
@Composable
private fun LiveTextField(
    value: String,
    identity: Any,
    onChange: (String) -> Unit,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null
) {
    var text by remember(identity) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { incoming ->
            // A hardware or clipboard newline would otherwise break the single-line layout.
            val cleaned = incoming.replace("\n", "")
            text = cleaned
            onChange(cleaned)
        },
        label = label,
        placeholder = placeholder,
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Work,
            unfocusedBorderColor = Line,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedLabelColor = Work,
            unfocusedLabelColor = TextDim
        )
    )
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (Exercise) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    CardSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveTextField(
                    value = exercise.name,
                    identity = exercise.id,
                    onChange = { onChange(exercise.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Exercise name", color = TextDim) },
                    capitalization = KeyboardCapitalization.Words
                )
                Column {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clickable(enabled = canMoveUp) { onMoveUp() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            "Move up",
                            tint = if (canMoveUp) TextDim else Line
                        )
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clickable(enabled = canMoveDown) { onMoveDown() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            "Move down",
                            tint = if (canMoveDown) TextDim else Line
                        )
                    }
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Clear, "Remove exercise", tint = TextDim)
                }
            }

            VSpace(6)
            DurationPicker(
                label = "Work",
                seconds = exercise.workSeconds,
                presets = WORK_PRESETS,
                accent = Work,
                onPick = { onChange(exercise.copy(workSeconds = it.coerceAtLeast(1))) }
            )
            VSpace(6)
            DurationPicker(
                label = "Rest",
                seconds = exercise.restSeconds,
                presets = REST_PRESETS,
                accent = Rest,
                onPick = { onChange(exercise.copy(restSeconds = it)) }
            )
        }
    }
}

/**
 * Typing "1:00" on a phone keyboard mid-thought is miserable, so durations are chosen from
 * taps: presets for the common values, plus fine adjustment either side.
 */
@Composable
private fun DurationPicker(
    label: String,
    seconds: Int,
    presets: List<Int>,
    accent: androidx.compose.ui.graphics.Color,
    onPick: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.width(44.dp)
        )
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .clickable { onPick((seconds - 5).coerceAtLeast(0)) },
            contentAlignment = Alignment.Center
        ) { Text("–", color = TextPrimary) }

        Text(
            formatClock(seconds),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(58.dp)
        )

        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2)
                .clickable { onPick(seconds + 5) },
            contentAlignment = Alignment.Center
        ) { Text("+", color = TextPrimary) }

        Box(Modifier.width(8.dp))

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(presets) { preset ->
                val selected = preset == seconds
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accent.copy(alpha = 0.18f) else Surface2)
                        .border(
                            1.dp,
                            if (selected) accent else Line,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onPick(preset) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (preset == 0) "none" else "${preset}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) accent else TextDim
                    )
                }
            }
        }
    }
}
