package com.emergencyblackbox

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "recording_events")
data class RecordingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val status: String
)

@Dao
interface RecordingEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: RecordingEvent): Long

    @Query("UPDATE recording_events SET endedAtEpochMs = :endedAt, status = :status WHERE id = :id")
    suspend fun markCompleted(id: Long, endedAt: Long, status: String)

    @Query("SELECT * FROM recording_events ORDER BY id DESC LIMIT 1")
    suspend fun getLastEvent(): RecordingEvent?
}

@Database(entities = [RecordingEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingEventDao(): RecordingEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emergency_blackbox.db"
                ).build().also { instance = it }
            }
        }
    }
}
