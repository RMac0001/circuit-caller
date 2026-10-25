package com.roger.circuitcaller.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Backups are plain JSON so they can be read, diffed, and stored anywhere — a text file in
 * a notes vault is a perfectly good place for one.
 */
object Backup {

    const val VERSION = 1

    fun export(workouts: List<WorkoutWithExercises>, settings: AppSettings): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("settings", JSONObject().apply {
            put("voiceEnabled", settings.voiceEnabled)
            put("countdownEnabled", settings.countdownEnabled)
            put("beepsEnabled", settings.beepsEnabled)
            put("vibrateEnabled", settings.vibrateEnabled)
            put("keepScreenOn", settings.keepScreenOn)
            put("halfwayCallout", settings.halfwayCallout)
            put("ttsVoiceName", settings.ttsVoiceName)
            put("speechRate", settings.speechRate.toDouble())
            put("prepSeconds", settings.prepSeconds)
        })

        val arr = JSONArray()
        workouts.forEach { w ->
            val ex = JSONArray()
            w.orderedExercises.forEach { e ->
                ex.put(JSONObject().apply {
                    put("name", e.name)
                    put("workSeconds", e.workSeconds)
                    put("restSeconds", e.restSeconds)
                })
            }
            arr.put(JSONObject().apply {
                put("name", w.workout.name)
                put("rounds", w.workout.rounds)
                put("defaultRestSeconds", w.workout.defaultRestSeconds)
                put("prepOverrideSeconds", w.workout.prepOverrideSeconds)
                put("exercises", ex)
            })
        }
        root.put("workouts", arr)
        return root.toString(2)
    }

    data class ParsedBackup(
        val workouts: List<Pair<Workout, List<Exercise>>>,
        val settings: AppSettings?
    )

    /**
     * Validates fully before returning anything. A malformed file should produce a clear
     * error, never a half-imported library.
     */
    fun parse(json: String): Result<ParsedBackup> = runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version == VERSION) {
            "This backup is version $version; this app reads version $VERSION."
        }

        val workoutsJson = root.optJSONArray("workouts")
            ?: throw IllegalArgumentException("No workouts found in this file.")

        val parsed = mutableListOf<Pair<Workout, List<Exercise>>>()
        for (i in 0 until workoutsJson.length()) {
            val wo = workoutsJson.getJSONObject(i)
            val workoutId = UUID.randomUUID().toString()
            val workout = Workout(
                id = workoutId,
                name = wo.optString("name", "Imported workout"),
                rounds = wo.optInt("rounds", 1).coerceIn(1, 30),
                defaultRestSeconds = wo.optInt("defaultRestSeconds", 30).coerceIn(0, 3600),
                prepOverrideSeconds = wo.optInt("prepOverrideSeconds", -1),
                sortIndex = i
            )
            val exJson = wo.optJSONArray("exercises") ?: JSONArray()
            val exercises = (0 until exJson.length()).map { j ->
                val e = exJson.getJSONObject(j)
                Exercise(
                    workoutId = workoutId,
                    name = e.optString("name", ""),
                    workSeconds = e.optInt("workSeconds", 60).coerceIn(1, 3600),
                    restSeconds = e.optInt("restSeconds", 0).coerceIn(0, 3600),
                    sortIndex = j
                )
            }
            parsed += workout to exercises
        }
        require(parsed.isNotEmpty()) { "This backup contains no workouts." }

        val settings = root.optJSONObject("settings")?.let { s ->
            AppSettings(
                voiceEnabled = s.optBoolean("voiceEnabled", true),
                countdownEnabled = s.optBoolean("countdownEnabled", true),
                beepsEnabled = s.optBoolean("beepsEnabled", true),
                vibrateEnabled = s.optBoolean("vibrateEnabled", true),
                keepScreenOn = s.optBoolean("keepScreenOn", true),
                halfwayCallout = s.optBoolean("halfwayCallout", false),
                ttsVoiceName = s.optString("ttsVoiceName", ""),
                speechRate = s.optDouble("speechRate", 1.0).toFloat(),
                prepSeconds = s.optInt("prepSeconds", 3)
            )
        }
        ParsedBackup(parsed, settings)
    }
}
