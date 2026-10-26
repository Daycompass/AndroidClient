package org.daycompass.android.buffer

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
import androidx.room.Transaction
import androidx.room.Upsert
import org.daycompass.android.api.Event

/** An observed event waiting to be accepted by the server. Carries no category, by design. */
@Entity(tableName = "buffered_event")
data class BufferedEvent(
    @PrimaryKey val eventId: String,
    val startTs: Long,
    val durationSecs: Long,
    val appId: String,
) {
    fun toEvent() = Event(eventId = eventId, startTs = startTs, durationSecs = durationSecs, appId = appId)
}

fun Event.toBuffered() = BufferedEvent(eventId = eventId, startTs = startTs, durationSecs = durationSecs, appId = appId)

/**
 * Where collection stands. There is no row until the device is paired, so nothing from before
 * pairing is ever read. The open session columns hold a session still running at [cursorMillis].
 */
@Entity(tableName = "collector_state")
data class CollectorState(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val cursorMillis: Long,
    val openPackage: String? = null,
    val openStartMillis: Long? = null,
    val openPausedAtMillis: Long? = null,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}

@Dao
interface BufferDao {
    @Query("SELECT * FROM collector_state WHERE id = ${CollectorState.SINGLE_ROW}")
    fun collectorState(): CollectorState?

    /** Starts collection at [state]'s cursor unless it has already started. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun startCollecting(state: CollectorState)

    @Upsert
    fun saveCollectorState(state: CollectorState)

    /** Re-reading a window yields the same `event_id`s; those are ignored here. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEvents(events: List<BufferedEvent>)

    /** Stores a collection run as one unit, so a crash never moves the cursor past lost events. */
    @Transaction
    fun saveRun(
        events: List<BufferedEvent>,
        state: CollectorState,
    ) {
        insertEvents(events)
        saveCollectorState(state)
    }

    @Query("SELECT * FROM buffered_event ORDER BY startTs, eventId LIMIT :limit")
    fun oldestEvents(limit: Int): List<BufferedEvent>

    @Query("DELETE FROM buffered_event WHERE eventId IN (:eventIds)")
    fun deleteEvents(eventIds: List<String>)

    @Query("SELECT COUNT(*) FROM buffered_event")
    fun eventCount(): Int

    /** Most recently collected first, for a status screen — never used to decide what to send. */
    @Query("SELECT * FROM buffered_event ORDER BY startTs DESC, eventId DESC LIMIT :limit")
    fun recentEvents(limit: Int): List<BufferedEvent>
}

@Database(entities = [BufferedEvent::class, CollectorState::class], version = 1)
abstract class BufferDatabase : RoomDatabase() {
    abstract fun bufferDao(): BufferDao

    companion object {
        @Volatile private var instance: BufferDatabase? = null

        fun get(context: Context): BufferDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, BufferDatabase::class.java, "event_buffer.db")
                    .build()
                    .also { instance = it }
            }
    }
}
