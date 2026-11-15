package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, PostEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OpenFeedDatabase : RoomDatabase() {
    abstract fun openFeedDao(): OpenFeedDao

    companion object {
        @Volatile
        private var INSTANCE: OpenFeedDatabase? = null

        fun getInstance(context: Context): OpenFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenFeedDatabase::class.java,
                    "openfeed_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
