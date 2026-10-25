package com.roger.circuitcaller.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.roger.circuitcaller.CircuitApp
import com.roger.circuitcaller.R
import com.roger.circuitcaller.data.AppSettings
import com.roger.circuitcaller.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything the UI needs to draw the runner. The service is the only writer. */
data class RunState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val workoutId: String = "",
    val workoutName: String = "",
    val totalRounds: Int = 1,
    val segments: List<Segment> = emptyList(),
    val index: Int = 0,
    val remainingMillis: Long = 0,
    val elapsedTotalSeconds: Int = 0
) {
    val current: Segment? get() = segments.getOrNull(index)

    val remainingSeconds: Int get() = ((remainingMillis + 999) / 1000).toInt()

    val segmentFraction: Float
        get() {
            val seg = current ?: return 0f
            if (seg.durationSeconds <= 0) return 0f
            return (remainingMillis / 1000f / seg.durationSeconds).coerceIn(0f, 1f)
        }

    val workoutRemainingSeconds: Int
        get() {
            if (segments.isEmpty()) return 0
            var t = (remainingMillis / 1000f).toInt()
            for (i in (index + 1) until segments.size) t += segments[i].durationSeconds
            return t
        }

    val nextWorkLabel: String?
        get() {
            for (i in (index + 1) until segments.size) {
                if (segments[i].type == SegmentType.WORK) return segments[i].label
            }
            return null
        }
}

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.roger.circuitcaller.START"
        const val ACTION_PAUSE_RESUME = "com.roger.circuitcaller.PAUSE_RESUME"
        const val ACTION_SKIP = "com.roger.circuitcaller.SKIP"
        const val ACTION_PREVIOUS = "com.roger.circuitcaller.PREVIOUS"
        const val ACTION_STOP = "com.roger.circuitcaller.STOP"
        const val EXTRA_WORKOUT_ID = "workout_id"

        private const val CHANNEL_ID = "circuit_caller_run"
        private const val NOTIFICATION_ID = 4711

        /** Shared so any screen can observe the run without binding. */
        private val _state = MutableStateFlow(RunState())
        val state: StateFlow<RunState> = _state.asStateFlow()

        fun start(context: Context, workoutId: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_WORKOUT_ID, workoutId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun send(context: Context, action: String) {
            context.startService(
                Intent(context, TimerService::class.java).apply {
                    this.action = action
                }
            )
        }

        fun clearFinished() {
            if (_state.value.finished) _state.value = RunState()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private lateinit var speaker: Speaker
    private var settings: AppSettings = AppSettings()
    private var wakeLock: PowerManager.WakeLock? = null

    /** Absolute deadline for the current segment; all display values derive from it. */
    private var deadlineElapsed: Long = 0
    private var pausedRemaining: Long = 0
    private var runStartedAt: Long = 0
    private var lastSpokenSecond: Int = -1
    private var halfwaySpoken: Boolean = false

    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        speaker = Speaker(this)
        speaker.init()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_WORKOUT_ID).orEmpty()
                if (id.isNotBlank()) startWorkout(id)
            }

            ACTION_PAUSE_RESUME -> togglePause()

            ACTION_SKIP -> jumpTo(_state.value.index + 1)

            ACTION_PREVIOUS -> previous()

            ACTION_STOP -> stopRun()
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- run control

    private fun startWorkout(workoutId: String) {
        scope.launch {
            val app = applicationContext as CircuitApp
            settings = app.settingsRepository.settings.first()
            speaker.rate = settings.speechRate
            speaker.voiceName = settings.ttsVoiceName

            val workout = app.database.workoutDao().getOne(workoutId) ?: return@launch
            val segments = SegmentBuilder.build(workout, settings.prepSeconds)
            if (segments.isEmpty()) return@launch

            _state.value = RunState(
                active = true,
                workoutId = workoutId,
                workoutName = workout.workout.name,
                totalRounds = workout.workout.rounds,
                segments = segments,
                index = 0
            )
            runStartedAt = SystemClock.elapsedRealtime()
            startForeground(NOTIFICATION_ID, buildNotification(_state.value))
            acquireWakeLock()
            enterSegment(0)
            startTicking()
        }
    }

    private fun enterSegment(index: Int) {
        val segments = _state.value.segments
        if (index >= segments.size) {
            finish()
            return
        }
        val target = index.coerceAtLeast(0)
        val seg = segments[target]

        deadlineElapsed = SystemClock.elapsedRealtime() + seg.durationSeconds * 1000L
        lastSpokenSecond = -1
        halfwaySpoken = false

        _state.value = _state.value.copy(
            index = target,
            remainingMillis = seg.durationSeconds * 1000L,
            paused = false
        )

        announceSegmentStart(seg)
        updateNotification()
    }

    private fun announceSegmentStart(seg: Segment) {
        speaker.stopSpeaking()
        when (seg.type) {
            SegmentType.PREP -> {
                if (seg.isFirstOfRound && seg.round > 1 && settings.voiceEnabled) {
                    speaker.say("Round ${seg.round}")
                }
                vibrate(longArrayOf(0, 40))
            }

            SegmentType.WORK -> {
                if (settings.voiceEnabled) speaker.say(seg.label)
                vibrate(longArrayOf(0, 80, 60, 80))
            }

            SegmentType.REST -> {
                if (settings.voiceEnabled) speaker.say("Rest")
                vibrate(longArrayOf(0, 120))
            }
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var lastNotificationSecond = -1
            while (true) {
                val s = _state.value
                if (!s.active || s.finished) break

                if (!s.paused) {
                    val remaining = (deadlineElapsed - SystemClock.elapsedRealtime())
                        .coerceAtLeast(0L)
                    val elapsedTotal =
                        ((SystemClock.elapsedRealtime() - runStartedAt) / 1000).toInt()
                    _state.value = s.copy(
                        remainingMillis = remaining,
                        elapsedTotalSeconds = elapsedTotal
                    )

                    val seg = s.current
                    if (seg != null) {
                        val whole = ((remaining + 999) / 1000).toInt()
                        if (whole != lastSpokenSecond) {
                            lastSpokenSecond = whole
                            onWholeSecond(seg, whole)
                        }
                        val halfway = seg.durationSeconds / 2
                        if (settings.halfwayCallout &&
                            seg.type == SegmentType.WORK &&
                            seg.durationSeconds >= 60 &&
                            !halfwaySpoken &&
                            whole <= halfway
                        ) {
                            halfwaySpoken = true
                            if (settings.voiceEnabled) speaker.say("Halfway")
                        }
                        if (whole != lastNotificationSecond) {
                            lastNotificationSecond = whole
                            updateNotification()
                        }
                    }

                    if (remaining <= 0L) {
                        if (settings.beepsEnabled) speaker.beep(highPitch = false)
                        enterSegment(_state.value.index + 1)
                    }
                }
                delay(100)
            }
        }
    }

    private fun onWholeSecond(seg: Segment, whole: Int) {
        if (whole !in 1..3) return
        if (seg.type == SegmentType.PREP) {
            if (settings.countdownEnabled && settings.voiceEnabled) speaker.say(whole.toString())
            if (settings.beepsEnabled) speaker.beep(highPitch = true)
        } else {
            if (settings.beepsEnabled) speaker.beep(highPitch = true)
        }
    }

    private fun togglePause() {
        val s = _state.value
        if (!s.active) return
        if (s.paused) {
            deadlineElapsed = SystemClock.elapsedRealtime() + pausedRemaining
            _state.value = s.copy(paused = false)
            acquireWakeLock()
        } else {
            pausedRemaining = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            _state.value = s.copy(paused = true, remainingMillis = pausedRemaining)
            speaker.stopSpeaking()
            releaseWakeLock()
        }
        updateNotification()
    }

    private fun previous() {
        val s = _state.value
        val seg = s.current ?: return
        val elapsedInSegment = seg.durationSeconds * 1000L - s.remainingMillis
        // More than two seconds in, "previous" restarts the current segment — the same
        // behavior every music player has, and what people reflexively expect.
        jumpTo(if (elapsedInSegment > 2000L) s.index else s.index - 1)
    }

    private fun jumpTo(index: Int) {
        val s = _state.value
        if (!s.active) return
        if (s.paused) _state.value = s.copy(paused = false)
        if (index >= s.segments.size) {
            finish()
        } else {
            enterSegment(index.coerceAtLeast(0))
        }
    }

    private fun finish() {
        tickJob?.cancel()
        val s = _state.value
        val worked = s.segments.filter { it.type == SegmentType.WORK }
        _state.value = s.copy(
            active = false,
            finished = true,
            paused = false,
            remainingMillis = 0,
            elapsedTotalSeconds = ((SystemClock.elapsedRealtime() - runStartedAt) / 1000).toInt()
        )
        if (settings.voiceEnabled) speaker.say("Workout complete")
        vibrate(longArrayOf(0, 200, 100, 200, 100, 320))

        scope.launch {
            val app = applicationContext as CircuitApp
            app.database.workoutDao().markRun(s.workoutId, System.currentTimeMillis())
        }
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
        // worked is referenced so the summary math stays close to the finish path
        if (worked.isEmpty()) return
    }

    private fun stopRun() {
        tickJob?.cancel()
        speaker.stopSpeaking()
        _state.value = RunState()
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    // ---------------------------------------------------------------- system bits

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CircuitCaller::run"
        ).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun vibrate(pattern: LongArray) {
        if (!settings.vibrateEnabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout in progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current exercise and time remaining"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(s: RunState): Notification {
        val seg = s.current
        val phase = when (seg?.type) {
            SegmentType.WORK -> seg.label
            SegmentType.REST -> "Rest"
            SegmentType.PREP -> "Get ready — ${seg.label}"
            null -> s.workoutName
        }
        val body = buildString {
            append(formatClock(s.remainingSeconds))
            append(" · Round ${seg?.round ?: 1} of ${s.totalRounds}")
            if (s.paused) append(" · Paused")
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(phase)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .addAction(0, if (s.paused) "Resume" else "Pause", actionIntent(ACTION_PAUSE_RESUME))
            .addAction(0, "Skip", actionIntent(ACTION_SKIP))
            .addAction(0, "Stop", actionIntent(ACTION_STOP))
            .build()
    }

    private fun updateNotification() {
        val s = _state.value
        if (!s.active) return
        runCatching {
            (getSystemService(NotificationManager::class.java))
                .notify(NOTIFICATION_ID, buildNotification(s))
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        releaseWakeLock()
        speaker.release()
        scope.cancel()
        focusListener = null
        super.onDestroy()
    }
}
