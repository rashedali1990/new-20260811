package com.example.m3uplayer.data.local.daos

import androidx.room.*
import com.example.m3uplayer.data.local.entities.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND date = :date ORDER BY startTime ASC")
    fun getProgramsForChannel(channelId: String, date: String): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE date < :expiryDate")
    suspend fun clearOldEpg(expiryDate: String)
}
