package com.example.m3uplayer.data.repository

import android.content.Context
import com.example.m3uplayer.data.local.AppDatabase
import com.example.m3uplayer.data.local.daos.*
import com.example.m3uplayer.data.local.entities.*
import com.example.m3uplayer.data.parser.M3uStreamingParser
import kotlinx.coroutines.flow.*
import java.io.InputStream

class MainRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val profileDao = db.profileDao()
    private val favoriteDao = db.favoriteDao()
    private val epgDao = db.epgDao()
    private val channelDao = db.channelDao()
    private val historyDao = db.historyDao()

    // Profiles
    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()
    fun getActiveProfile(): Flow<ProfileEntity?> = profileDao.getActiveProfile()
    suspend fun saveProfile(profile: ProfileEntity) = profileDao.insertProfile(profile)
    suspend fun setActiveProfile(id: Int) {
        profileDao.deactivateAll()
        profileDao.setActiveProfile(id)
    }

    // Channels
    fun getChannels(profileId: Int): Flow<List<ChannelEntity>> = channelDao.getChannelsForProfile(profileId)
    suspend fun parseAndSavePlaylist(inputStream: InputStream, profileId: Int, onProgress: (Int) -> Unit) {
        val channels = M3uStreamingParser.parseStreaming(inputStream, profileId, onProgress)
        channelDao.clearChannelsForProfile(profileId)
        channelDao.insertChannels(channels)
    }

    // Favorites
    fun getFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    suspend fun toggleFavorite(channel: ChannelEntity) {
        val existing = favoriteDao.isFavorite(channel.url)
        if (existing != null) {
            favoriteDao.removeFavorite(existing)
        } else {
            favoriteDao.insertFavorite(FavoriteEntity(
                mediaId = channel.url,
                name = channel.name,
                url = channel.url,
                logoUrl = channel.logoUrl,
                groupTitle = channel.groupTitle
            ))
        }
    }

    // EPG
    fun getPrograms(channelId: String, date: String): Flow<List<EpgProgramEntity>> = epgDao.getProgramsForChannel(channelId, date)
    suspend fun saveEpgPrograms(programs: List<EpgProgramEntity>) = epgDao.insertPrograms(programs)

    // History
    fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getWatchHistory()
    suspend fun updateHistory(mediaId: String, name: String, position: Long) {
        historyDao.updateHistory(HistoryEntity(mediaId = mediaId, name = name, position = position))
    }
}
