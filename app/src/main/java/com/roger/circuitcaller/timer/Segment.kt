package com.roger.circuitcaller.timer

import com.roger.circuitcaller.data.WorkoutWithExercises

enum class SegmentType { PREP, WORK, REST }

data class Segment(
    val type: SegmentType,
    val durationSeconds: Int,
    /** Exercise name for PREP and WORK; "Rest" for REST. */
    val label: String,
    val round: Int,
    val exerciseIndex: Int,
    val isFirstOfRound: Boolean
)

object SegmentBuilder {

    /**
     * Expands a workout into the flat segment list the runner plays through.
     *
     * Rules:
     *  - blank-named exercises are dropped entirely
     *  - a PREP segment precedes each WORK segment when prepSeconds > 0
     *  - the trailing REST of the final round is omitted; being told to rest and then
     *    immediately told the workout is over is pointless
     */
    fun build(workout: WorkoutWithExercises, globalPrepSeconds: Int): List<Segment> {
        val exercises = workout.usableExercises
        if (exercises.isEmpty()) return emptyList()

        val prep = if (workout.workout.prepOverrideSeconds >= 0) {
            workout.workout.prepOverrideSeconds
        } else {
            globalPrepSeconds
        }

        val rounds = workout.workout.rounds.coerceAtLeast(1)
        val out = mutableListOf<Segment>()

        for (round in 1..rounds) {
            exercises.forEachIndexed { index, ex ->
                if (prep > 0) {
                    out += Segment(
                        type = SegmentType.PREP,
                        durationSeconds = prep,
                        label = ex.name,
                        round = round,
                        exerciseIndex = index,
                        isFirstOfRound = index == 0
                    )
                }
                out += Segment(
                    type = SegmentType.WORK,
                    durationSeconds = ex.workSeconds.coerceAtLeast(1),
                    label = ex.name,
                    round = round,
                    exerciseIndex = index,
                    isFirstOfRound = index == 0
                )
                val isFinalSegmentOfWorkout = round == rounds && index == exercises.lastIndex
                if (ex.restSeconds > 0 && !isFinalSegmentOfWorkout) {
                    out += Segment(
                        type = SegmentType.REST,
                        durationSeconds = ex.restSeconds,
                        label = "Rest",
                        round = round,
                        exerciseIndex = index,
                        isFirstOfRound = false
                    )
                }
            }
        }
        return out
    }

    fun totalSeconds(workout: WorkoutWithExercises, globalPrepSeconds: Int): Int =
        build(workout, globalPrepSeconds).sumOf { it.durationSeconds }
}

fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatClock(totalSeconds: Long): String = formatClock(totalSeconds.toInt())
