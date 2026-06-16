package com.varro.hearing.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert suspend fun insert(entry: DiaryEntry)
    @Query("SELECT * FROM diary ORDER BY time DESC LIMIT 500") fun recent(): Flow<List<DiaryEntry>>
    @Query("DELETE FROM diary") suspend fun clear()
    @Query("SELECT * FROM diary ORDER BY time DESC") suspend fun all(): List<DiaryEntry>
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: Schedule): Long
    @Update suspend fun update(s: Schedule)
    @Delete suspend fun delete(s: Schedule)
    @Query("SELECT * FROM schedules ORDER BY hour, minute") fun all(): Flow<List<Schedule>>
    @Query("SELECT * FROM schedules WHERE enabled = 1") suspend fun enabled(): List<Schedule>
    @Query("SELECT * FROM schedules WHERE id = :id") suspend fun byId(id: Long): Schedule?
}

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: Place): Long
    @Delete suspend fun delete(p: Place)
    @Query("SELECT * FROM places ORDER BY label") fun all(): Flow<List<Place>>
    @Query("SELECT * FROM places WHERE enabled = 1") suspend fun enabled(): List<Place>
    @Query("SELECT * FROM places WHERE id = :id") suspend fun byId(id: Long): Place?
}
