package com.roger.circuitcaller.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.roger.circuitcaller.BuildConfig
import com.roger.circuitcaller.timer.TimerService
import com.roger.circuitcaller.ui.editor.EditorScreen
import com.roger.circuitcaller.ui.library.LibraryScreen
import com.roger.circuitcaller.ui.run.DoneScreen
import com.roger.circuitcaller.ui.run.RunScreen
import com.roger.circuitcaller.ui.settings.SettingsScreen
import com.roger.circuitcaller.ui.theme.Background
import com.roger.circuitcaller.ui.theme.CircuitCallerTheme
import com.roger.circuitcaller.ui.theme.Surface
import com.roger.circuitcaller.ui.theme.TextPrimary
import com.roger.circuitcaller.ui.theme.Work
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CircuitCallerTheme {
                // Surface supplies the theme's light foreground color to plain Text and Icon
                // composables. A background-only Box does not, which caused default text to
                // render black against the dark app background on some screens.
                MaterialSurface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background,
                    contentColor = TextPrimary
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        AppRoot(
                            onKeepScreenOn = { on ->
                                if (on) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(onKeepScreenOn: (Boolean) -> Unit) {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val runState by TimerService.state.collectAsStateWithLifecycle()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The runner and completion screens are driven by service state, not navigation events,
    // so reopening the app mid-workout always lands on the right screen.
    LaunchedEffect(runState.active, runState.finished) {
        when {
            runState.active -> navController.navigate("run") {
                launchSingleTop = true
            }

            runState.finished -> navController.navigate("done") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(runState.active, settings.keepScreenOn) {
        onKeepScreenOn(runState.active && settings.keepScreenOn)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = vm.buildBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                }.onSuccess {
                    snackbarHostState.showSnackbar("Backup saved")
                }.onFailure {
                    snackbarHostState.showSnackbar("Could not write that file")
                }
            }
        }
    }

    val update by vm.update.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Returning from "Install unknown apps" re-checks the permission and carries on.
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onReturnedFromInstallPermission() }

    if (update.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = vm::cancelInstallPermission,
            title = { Text("Allow updates") },
            text = {
                Text(
                    "Android needs permission for Circuit Caller to install its own updates. " +
                        "Turn on \"Allow from this source\", then come back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.onInstallPermissionPromptShown()
                    installPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Open settings", color = Work) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelInstallPermission) { Text("Not now") }
            },
            containerColor = Surface
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                }.getOrNull()
                if (text == null) {
                    snackbarHostState.showSnackbar("Could not read that file")
                } else {
                    vm.importBackup(text) { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                LaunchedEffect(Unit) { vm.autoCheckForUpdate() }
                LibraryScreen(
                    workouts = workouts,
                    settings = settings,
                    onOpen = { navController.navigate("editor/$it") },
                    onStart = { TimerService.start(context, it) },
                    onNew = {
                        vm.createWorkout { id -> navController.navigate("editor/$id") }
                    },
                    onSettings = { navController.navigate("settings") },
                    onExport = { exportLauncher.launch("circuit-caller-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    update = update,
                    versionName = BuildConfig.VERSION_NAME,
                    onCheckForUpdates = vm::checkForUpdateNow,
                    onUpdate = vm::startUpdate,
                    onDismissUpdate = vm::dismissUpdate
                )
            }

            composable("editor/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val workout by vm.workoutFlow(id).collectAsStateWithLifecycle(initialValue = null)
                workout?.let { w ->
                    EditorScreen(
                        workout = w,
                        globalPrepSeconds = settings.prepSeconds,
                        onBack = { navController.popBackStack() },
                        onRename = { vm.updateWorkout(w.workout.copy(name = it)) },
                        onRounds = { vm.updateWorkout(w.workout.copy(rounds = it)) },
                        onDefaultRest = { vm.updateWorkout(w.workout.copy(defaultRestSeconds = it)) },
                        onAddExercise = { vm.addExercise(w) },
                        onUpdateExercise = { vm.updateExercise(it) },
                        onDeleteExercise = { vm.deleteExercise(w, it) },
                        onMoveExercise = { from, direction -> vm.moveExercise(w, from, direction) },
                        onDuplicate = {
                            vm.duplicateWorkout(w) { newId ->
                                navController.navigate("editor/$newId") { popUpTo("library") }
                            }
                        },
                        onDelete = {
                            vm.deleteWorkout(w.workout.id)
                            navController.popBackStack()
                        },
                        onStart = { TimerService.start(context, w.workout.id) }
                    )
                }
            }

            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onVoice = vm::setVoiceEnabled,
                    onCountdown = vm::setCountdown,
                    onBeeps = vm::setBeeps,
                    onVibrate = vm::setVibrate,
                    onKeepScreenOn = vm::setKeepScreenOn,
                    onHalfway = vm::setHalfway,
                    onVoiceName = vm::setVoiceName,
                    onRate = vm::setRate,
                    onPrep = vm::setPrep
                )
            }

            composable("run") {
                RunScreen(
                    state = runState,
                    onPauseResume = {
                        TimerService.send(context, TimerService.ACTION_PAUSE_RESUME)
                    },
                    onSkip = { TimerService.send(context, TimerService.ACTION_SKIP) },
                    onPrevious = { TimerService.send(context, TimerService.ACTION_PREVIOUS) },
                    onStop = {
                        TimerService.send(context, TimerService.ACTION_STOP)
                        navController.popBackStack("library", inclusive = false)
                    }
                )
            }

            composable("done") {
                DoneScreen(
                    state = runState,
                    onAgain = {
                        val id = runState.workoutId
                        TimerService.clearFinished()
                        TimerService.start(context, id)
                    },
                    onHome = {
                        TimerService.clearFinished()
                        navController.popBackStack("library", inclusive = false)
                    }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
