package com.example.m3uplayer.data.local.daos

import androidx.room.*
import com.example.m3uplayer.data.local.entities.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE profileId = :profileId")
    fun getChannelsForProfile(profileId: Int): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun clearChannelsForProfile(profileId: Int)
}
