package com.roger.circuitcaller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun exportThenParseRoundTrips() {
        val workout =
            Workout(id = "w", name = "Upper body", rounds = 3, defaultRestSeconds = 20, prepOverrideSeconds = 5)
        val exercises = listOf(
            Exercise(workoutId = "w", name = "Flys", workSeconds = 45, restSeconds = 15, sortIndex = 1),
            Exercise(workoutId = "w", name = "Press", workSeconds = 60, restSeconds = 30, sortIndex = 0)
        )
        val settings = AppSettings(voiceEnabled = false, speechRate = 1.5f, prepSeconds = 7, ttsVoiceName = "en-gb")

        val parsed = Backup.parse(
            Backup.export(listOf(WorkoutWithExercises(workout, exercises)), settings)
        ).getOrThrow()

        val (w, ex) = parsed.workouts.single()
        assertEquals("Upper body", w.name)
        assertEquals(3, w.rounds)
        assertEquals(20, w.defaultRestSeconds)
        assertEquals(5, w.prepOverrideSeconds)
        assertEquals(listOf("Press", "Flys"), ex.map { it.name })
        assertEquals(listOf(60, 45), ex.map { it.workSeconds })
        assertEquals(listOf(30, 15), ex.map { it.restSeconds })
        assertEquals(listOf(0, 1), ex.map { it.sortIndex })
        assertTrue(ex.all { it.workoutId == w.id })
        assertEquals(settings, parsed.settings)
    }

    @Test
    fun importedWorkoutsGetNewIds() {
        val json = Backup.export(listOf(WorkoutWithExercises(Workout(id = "original"), emptyList())), AppSettings())
        val (w, _) = Backup.parse(json).getOrThrow().workouts.single()
        assertTrue(w.id != "original")
    }

    @Test
    fun rejectsOtherVersions() {
        val error = Backup.parse("""{"version": 2, "workouts": []}""").exceptionOrNull()
        assertEquals("This backup is version 2; this app reads version 1.", error?.message)
    }

    @Test
    fun rejectsFilesWithoutWorkouts() {
        assertEquals(
            "No workouts found in this file.",
            Backup.parse("""{"version": 1}""").exceptionOrNull()?.message
        )
        assertEquals(
            "This backup contains no workouts.",
            Backup.parse("""{"version": 1, "workouts": []}""").exceptionOrNull()?.message
        )
    }

    @Test
    fun rejectsMalformedJson() {
        assertTrue(Backup.parse("not json").isFailure)
    }

    @Test
    fun clampsOutOfRangeValues() {
        val json = """
            {"version": 1, "workouts": [{"name": "W", "rounds": 99, "defaultRestSeconds": -1,
              "exercises": [{"name": "A", "workSeconds": 0, "restSeconds": 9999}]}]}
        """.trimIndent()
        val (w, ex) = Backup.parse(json).getOrThrow().workouts.single()
        assertEquals(30, w.rounds)
        assertEquals(0, w.defaultRestSeconds)
        assertEquals(1, ex.single().workSeconds)
        assertEquals(3600, ex.single().restSeconds)
    }

    @Test
    fun settingsAreOptional() {
        val json = """{"version": 1, "workouts": [{"name": "W"}]}"""
        val parsed = Backup.parse(json).getOrThrow()
        assertNull(parsed.settings)
        assertEquals(-1, parsed.workouts.single().first.prepOverrideSeconds)
        assertTrue(parsed.workouts.single().second.isEmpty())
    }
}
