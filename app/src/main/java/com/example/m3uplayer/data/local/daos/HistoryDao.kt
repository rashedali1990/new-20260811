package com.example.m3uplayer.data.local.daos

import androidx.room.*
import com.example.m3uplayer.data.local.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatched DESC")
    fun getWatchHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateHistory(history: HistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()
}
