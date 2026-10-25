package com.roger.circuitcaller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roger.circuitcaller.CircuitApp
import com.roger.circuitcaller.data.AppSettings
import com.roger.circuitcaller.data.Backup
import com.roger.circuitcaller.data.Exercise
import com.roger.circuitcaller.data.Workout
import com.roger.circuitcaller.data.WorkoutWithExercises
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as CircuitApp
    private val dao = application.database.workoutDao()
    private val settingsRepo = application.settingsRepository

    val workouts: StateFlow<List<WorkoutWithExercises>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettings()
        )

    fun workoutFlow(id: String) = dao.observeOne(id)

    // ------------------------------------------------------------------ workouts

    fun createWorkout(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val nextSort = (workouts.value.maxOfOrNull { it.workout.sortIndex } ?: -1) + 1
            dao.upsertWorkout(Workout(id = id, name = "", sortIndex = nextSort))
            dao.upsertExercise(Exercise(workoutId = id, name = "", workSeconds = 60, restSeconds = 30))
            onCreated(id)
        }
    }

    fun updateWorkout(workout: Workout) {
        viewModelScope.launch { dao.updateWorkout(workout) }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch { dao.deleteWorkout(id) }
    }

    fun duplicateWorkout(source: WorkoutWithExercises, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val nextSort = (workouts.value.maxOfOrNull { it.workout.sortIndex } ?: -1) + 1
            dao.upsertWorkout(
                source.workout.copy(
                    id = id,
                    name = source.workout.name + " (copy)",
                    createdAt = System.currentTimeMillis(),
                    lastRunAt = null,
                    sortIndex = nextSort
                )
            )
            dao.upsertExercises(
                source.orderedExercises.mapIndexed { index, e ->
                    e.copy(id = UUID.randomUUID().toString(), workoutId = id, sortIndex = index)
                }
            )
            onCreated(id)
        }
    }

    // ------------------------------------------------------------------ exercises

    fun addExercise(workout: WorkoutWithExercises) {
        viewModelScope.launch {
            val nextSort = (workout.exercises.maxOfOrNull { it.sortIndex } ?: -1) + 1
            dao.upsertExercise(
                Exercise(
                    workoutId = workout.workout.id,
                    name = "",
                    workSeconds = 60,
                    restSeconds = workout.workout.defaultRestSeconds,
                    sortIndex = nextSort
                )
            )
        }
    }

    fun updateExercise(exercise: Exercise) {
        viewModelScope.launch { dao.upsertExercise(exercise) }
    }

    fun deleteExercise(workout: WorkoutWithExercises, exercise: Exercise) {
        viewModelScope.launch {
            dao.deleteExercise(exercise.id)
            // Keep at least one row so the editor is never an empty void
            if (workout.exercises.size <= 1) {
                dao.upsertExercise(
                    Exercise(
                        workoutId = workout.workout.id,
                        name = "",
                        workSeconds = 60,
                        restSeconds = workout.workout.defaultRestSeconds,
                        sortIndex = 0
                    )
                )
            }
        }
    }

    fun moveExercise(workout: WorkoutWithExercises, from: Int, direction: Int) {
        viewModelScope.launch {
            val ordered = workout.orderedExercises.toMutableList()
            val to = from + direction
            if (from !in ordered.indices || to !in ordered.indices) return@launch
            val item = ordered.removeAt(from)
            ordered.add(to, item)
            dao.upsertExercises(ordered.mapIndexed { index, e -> e.copy(sortIndex = index) })
        }
    }

    // ------------------------------------------------------------------ settings

    fun setVoiceEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setVoiceEnabled(v) }
    fun setCountdown(v: Boolean) = viewModelScope.launch { settingsRepo.setCountdownEnabled(v) }
    fun setBeeps(v: Boolean) = viewModelScope.launch { settingsRepo.setBeepsEnabled(v) }
    fun setVibrate(v: Boolean) = viewModelScope.launch { settingsRepo.setVibrateEnabled(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(v) }
    fun setHalfway(v: Boolean) = viewModelScope.launch { settingsRepo.setHalfwayCallout(v) }
    fun setVoiceName(v: String) = viewModelScope.launch { settingsRepo.setTtsVoiceName(v) }
    fun setRate(v: Float) = viewModelScope.launch { settingsRepo.setSpeechRate(v) }
    fun setPrep(v: Int) = viewModelScope.launch { settingsRepo.setPrepSeconds(v) }

    // ------------------------------------------------------------------ backup

    suspend fun buildBackupJson(): String =
        Backup.export(dao.getAll(), settingsRepo.settings.first())

    fun importBackup(json: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            Backup.parse(json).fold(
                onSuccess = { parsed ->
                    var sort = (workouts.value.maxOfOrNull { it.workout.sortIndex } ?: -1) + 1
                    parsed.workouts.forEach { (workout, exercises) ->
                        dao.upsertWorkout(workout.copy(sortIndex = sort++))
                        dao.upsertExercises(exercises)
                    }
                    parsed.settings?.let { settingsRepo.applyAll(it) }
                    val n = parsed.workouts.size
                    onResult("Imported $n workout${if (n == 1) "" else "s"}")
                },
                onFailure = { onResult(it.message ?: "That file could not be read") }
            )
        }
    }
}
