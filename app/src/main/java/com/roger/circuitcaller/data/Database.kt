package com.roger.circuitcaller.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "New workout",
    val rounds: Int = 1,
    @ColumnInfo(name = "default_rest") val defaultRestSeconds: Int = 30,
    /** -1 means "use the global prep setting". */
    @ColumnInfo(name = "prep_override") val prepOverrideSeconds: Int = -1,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long? = null,
    @ColumnInfo(name = "sort_index") val sortIndex: Int = 0
)

@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(
        entity = Workout::class,
        parentColumns = ["id"],
        childColumns = ["workout_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workout_id")]
)
data class Exercise(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "workout_id") val workoutId: String,
    val name: String = "",
    @ColumnInfo(name = "work_seconds") val workSeconds: Int = 60,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int = 30,
    @ColumnInfo(name = "sort_index") val sortIndex: Int = 0
)

data class WorkoutWithExercises(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workout_id")
    val exercises: List<Exercise>
) {
    /** Exercises in display order, blanks excluded — the list the runner actually uses. */
    val usableExercises: List<Exercise>
        get() = exercises.sortedBy { it.sortIndex }.filter { it.name.isNotBlank() }

    val orderedExercises: List<Exercise>
        get() = exercises.sortedBy { it.sortIndex }
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY sort_index ASC, created_at ASC")
    fun observeAll(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeOne(id: String): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getOne(id: String): WorkoutWithExercises?

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY sort_index ASC")
    suspend fun getAll(): List<WorkoutWithExercises>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(workout: Workout)

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(exercises: List<Exercise>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: String)

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    @Query("UPDATE workouts SET last_run_at = :at WHERE id = :id")
    suspend fun markRun(id: String, at: Long)
}

@Database(entities = [Workout::class, Exercise::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
