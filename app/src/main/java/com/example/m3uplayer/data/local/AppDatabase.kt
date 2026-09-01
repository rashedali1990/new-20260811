package com.example.m3uplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.m3uplayer.data.local.daos.*
import com.example.m3uplayer.data.local.entities.*

@Database(
    entities = [
        ProfileEntity::class,
        FavoriteEntity::class,
        EpgProgramEntity::class,
        ChannelEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun epgDao(): EpgDao
    abstract fun channelDao(): ChannelDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "m3uplayer_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
