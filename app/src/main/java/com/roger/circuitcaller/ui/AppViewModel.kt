package com.roger.circuitcaller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roger.circuitcaller.BuildConfig
import com.roger.circuitcaller.CircuitApp
import com.roger.circuitcaller.data.AppSettings
import com.roger.circuitcaller.data.Backup
import com.roger.circuitcaller.data.Exercise
import com.roger.circuitcaller.data.Workout
import com.roger.circuitcaller.data.WorkoutWithExercises
import com.roger.circuitcaller.timer.TimerService
import com.roger.circuitcaller.update.InstallEvents
import com.roger.circuitcaller.update.Release
import com.roger.circuitcaller.update.UpdateClient
import com.roger.circuitcaller.update.UpdateInstaller
import com.roger.circuitcaller.update.UpdatePhase
import com.roger.circuitcaller.update.UpdateState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    suspend fun buildBackupJson(): String = Backup.export(dao.getAll(), settingsRepo.settings.first())

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

    // ------------------------------------------------------------------ updates

    private val updateClient = UpdateClient(BuildConfig.VERSION_NAME)
    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /** One-off snackbar messages. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var downloadedApk: File? = null

    private val workoutRunning: Boolean get() = TimerService.state.value.active

    init {
        // A workout starting mid-check or mid-download stops it; the card stays so it can be retried.
        viewModelScope.launch {
            TimerService.state.map { it.active }.distinctUntilChanged().filter { it }.collect {
                checkJob?.cancel()
                if (downloadJob?.isActive == true) {
                    downloadJob?.cancel()
                    _update.update { s -> s.copy(phase = UpdatePhase.IDLE, percent = null) }
                }
            }
        }
        viewModelScope.launch {
            InstallEvents.failure.filterNotNull().collect { message ->
                InstallEvents.consumeFailure()
                _update.update { it.copy(phase = UpdatePhase.IDLE, percent = null) }
                _messages.send(message)
            }
        }
    }

    /** Runs when the library opens: at most once a day, silent on failure, quiet for a dismissed version. */
    fun autoCheckForUpdate() {
        if (workoutRunning || checkJob?.isActive == true || _update.value.available != null) return
        checkJob = viewModelScope.launch {
            val prefs = settingsRepo.updatePrefs.first()
            val now = System.currentTimeMillis()
            if (now - prefs.lastCheckAt in 0 until UPDATE_CHECK_INTERVAL_MS) return@launch
            settingsRepo.setUpdateLastCheck(now)
            val release = updateClient.fetchLatest().getOrNull() ?: return@launch
            val dismissed = prefs.dismissedVersion.isNotBlank() &&
                !Release.isNewer(release.version, prefs.dismissedVersion)
            if (Release.isNewer(release.version, BuildConfig.VERSION_NAME) && !dismissed && !workoutRunning) {
                _update.update { it.copy(available = release) }
            }
        }
    }

    /** The footer link: always reports back, and shows a dismissed version again. */
    fun checkForUpdateNow() {
        if (workoutRunning) {
            _messages.trySend("Updates wait until the workout is over")
            return
        }
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            settingsRepo.setUpdateLastCheck(System.currentTimeMillis())
            updateClient.fetchLatest().fold(
                onSuccess = { release ->
                    if (Release.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                        _update.update { it.copy(available = release) }
                    } else {
                        _messages.send("You're on the latest version (${BuildConfig.VERSION_NAME})")
                    }
                },
                onFailure = { _messages.send(it.message ?: "Couldn't check for updates") }
            )
        }
    }

    fun dismissUpdate() {
        val release = _update.value.available ?: return
        if (_update.value.phase != UpdatePhase.IDLE) return
        _update.value = UpdateState()
        viewModelScope.launch { settingsRepo.setUpdateDismissed(release.version) }
    }

    fun startUpdate() {
        val release = _update.value.available ?: return
        if (_update.value.phase != UpdatePhase.IDLE) return
        if (workoutRunning) {
            _messages.trySend("Updates wait until the workout is over")
            return
        }
        val cached = downloadedApk
        if (cached != null && cached.exists() && cached.name == "circuit-caller-${release.version}.apk") {
            viewModelScope.launch { installDownloaded() }
            return
        }
        _update.update { it.copy(phase = UpdatePhase.DOWNLOADING, percent = 0) }
        downloadJob = viewModelScope.launch {
            updateClient.download(release, UpdateInstaller.updatesDir(application)) { percent ->
                _update.update { it.copy(percent = percent) }
            }.fold(
                onSuccess = { file ->
                    downloadedApk = file
                    installDownloaded()
                },
                onFailure = { e ->
                    _update.update { it.copy(phase = UpdatePhase.IDLE, percent = null) }
                    _messages.send(e.message ?: "Download failed")
                }
            )
        }
    }

    /** The user chose to open Settings from the permission dialog. */
    fun onInstallPermissionPromptShown() {
        _update.update { it.copy(needsInstallPermission = false) }
    }

    /** The user dismissed the permission dialog without opening Settings. */
    fun cancelInstallPermission() {
        _update.update { it.copy(phase = UpdatePhase.IDLE, percent = null, needsInstallPermission = false) }
    }

    /** Back from "Install unknown apps": carry on if it was allowed. */
    fun onReturnedFromInstallPermission() {
        if (UpdateInstaller.canInstall(application)) {
            viewModelScope.launch { installDownloaded() }
        } else {
            cancelInstallPermission()
            _messages.trySend("The update needs permission to install")
        }
    }

    private suspend fun installDownloaded() {
        val apk = downloadedApk?.takeIf { it.exists() }
        if (apk == null || workoutRunning) {
            _update.update { it.copy(phase = UpdatePhase.IDLE, percent = null) }
            if (workoutRunning) _messages.send("Updates wait until the workout is over")
            return
        }
        if (!UpdateInstaller.canInstall(application)) {
            _update.update { it.copy(phase = UpdatePhase.INSTALLING, needsInstallPermission = true) }
            return
        }
        _update.update { it.copy(phase = UpdatePhase.INSTALLING, needsInstallPermission = false) }
        runCatching { UpdateInstaller.install(application, apk) }.onFailure {
            _update.update { s -> s.copy(phase = UpdatePhase.IDLE, percent = null) }
            _messages.send("Couldn't start the install")
        }
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
