package com.roger.circuitcaller.timer

import com.roger.circuitcaller.data.Exercise
import com.roger.circuitcaller.data.Workout
import com.roger.circuitcaller.data.WorkoutWithExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentBuilderTest {

    private fun workout(
        rounds: Int = 1,
        prepOverride: Int = -1,
        vararg exercises: Triple<String, Int, Int>
    ): WorkoutWithExercises {
        val w = Workout(id = "w", rounds = rounds, prepOverrideSeconds = prepOverride)
        return WorkoutWithExercises(
            w,
            exercises.mapIndexed { i, (name, work, rest) ->
                Exercise(workoutId = "w", name = name, workSeconds = work, restSeconds = rest, sortIndex = i)
            }
        )
    }

    @Test
    fun noUsableExercisesGivesNoSegments() {
        assertTrue(SegmentBuilder.build(workout(), 3).isEmpty())
        assertTrue(SegmentBuilder.build(workout(1, -1, Triple("  ", 60, 30)), 3).isEmpty())
    }

    @Test
    fun prepPrecedesEachWorkAndFinalRestIsOmitted() {
        val segments = SegmentBuilder.build(
            workout(2, -1, Triple("Press", 60, 30), Triple("Flys", 45, 15)),
            globalPrepSeconds = 3
        )
        assertEquals(
            listOf(
                "PREP Press 3", "WORK Press 60", "REST Rest 30",
                "PREP Flys 3", "WORK Flys 45", "REST Rest 15",
                "PREP Press 3", "WORK Press 60", "REST Rest 30",
                "PREP Flys 3", "WORK Flys 45"
            ),
            segments.map { "${it.type} ${it.label} ${it.durationSeconds}" }
        )
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2), segments.map { it.round })
    }

    @Test
    fun firstOfRoundMarksOnlyTheFirstExercise() {
        val segments = SegmentBuilder.build(workout(2, -1, Triple("A", 10, 5), Triple("B", 10, 5)), 0)
        assertEquals(
            listOf("A" to true, "B" to false, "A" to true, "B" to false),
            segments.filter { it.type == SegmentType.WORK }.map { it.label to it.isFirstOfRound }
        )
        assertTrue(segments.filter { it.type == SegmentType.REST }.none { it.isFirstOfRound })
    }

    @Test
    fun prepOverrideWinsOverGlobalSetting() {
        val noPrep = SegmentBuilder.build(workout(1, 0, Triple("A", 10, 0)), globalPrepSeconds = 3)
        assertEquals(listOf(SegmentType.WORK), noPrep.map { it.type })

        val longPrep = SegmentBuilder.build(workout(1, 10, Triple("A", 10, 0)), globalPrepSeconds = 3)
        assertEquals(10, longPrep.first { it.type == SegmentType.PREP }.durationSeconds)
    }

    @Test
    fun blankExercisesAreDroppedAndOrderFollowsSortIndex() {
        val w = WorkoutWithExercises(
            Workout(id = "w"),
            listOf(
                Exercise(workoutId = "w", name = "Second", sortIndex = 2, restSeconds = 0),
                Exercise(workoutId = "w", name = "", sortIndex = 1, restSeconds = 0),
                Exercise(workoutId = "w", name = "First", sortIndex = 0, restSeconds = 0)
            )
        )
        val work = SegmentBuilder.build(w, 0)
        assertEquals(listOf("First", "Second"), work.map { it.label })
        assertEquals(listOf(0, 1), work.map { it.exerciseIndex })
    }

    @Test
    fun roundsAndWorkSecondsHaveAFloorOfOne() {
        val segments = SegmentBuilder.build(workout(0, -1, Triple("A", 0, 0)), 0)
        assertEquals(1, segments.size)
        assertEquals(1, segments.single().durationSeconds)
    }

    @Test
    fun totalSecondsSumsEverySegment() {
        // Round 1: 3+60+30 + 3+45+15 = 156. Round 2: 3+60+30 + 3+45 = 141 (no final rest).
        val w = workout(2, -1, Triple("Press", 60, 30), Triple("Flys", 45, 15))
        assertEquals(297, SegmentBuilder.totalSeconds(w, 3))
    }

    @Test
    fun formatClockShowsMinutesAndPaddedSeconds() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:59", formatClock(59))
        assertEquals("1:00", formatClock(60))
        assertEquals("59:59", formatClock(3599))
        assertEquals("60:00", formatClock(3600))
        assertEquals("0:00", formatClock(-5))
        assertEquals("2:05", formatClock(125L))
    }
}
