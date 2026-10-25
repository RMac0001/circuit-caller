package com.roger.circuitcaller

import android.app.Application
import androidx.room.Room
import com.roger.circuitcaller.data.AppDatabase
import com.roger.circuitcaller.data.Exercise
import com.roger.circuitcaller.data.SettingsRepository
import com.roger.circuitcaller.data.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class CircuitApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "circuit-caller.db").build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedIfEmpty() }
    }

    /** A first-launch example so the library is never a blank wall. */
    private suspend fun seedIfEmpty() {
        val dao = database.workoutDao()
        if (dao.count() > 0) return

        val workoutId = UUID.randomUUID().toString()
        dao.upsertWorkout(
            Workout(
                id = workoutId,
                name = "Upper body circuit",
                rounds = 2,
                defaultRestSeconds = 30,
                sortIndex = 0
            )
        )
        val samples = listOf(
            Triple("Wall Press", 60, 30),
            Triple("Flys", 60, 30),
            Triple("Shoulder Rolls", 45, 30),
            Triple("Arm Circles", 45, 30)
        )
        dao.upsertExercises(
            samples.mapIndexed { index, (name, work, rest) ->
                Exercise(
                    workoutId = workoutId,
                    name = name,
                    workSeconds = work,
                    restSeconds = rest,
                    sortIndex = index
                )
            }
        )
    }
}
