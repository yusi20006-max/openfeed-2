package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChannelEntity::class, PostEntity::class, CachedArticleEntity::class],
    version = 4,
    exportSchema = false
)
abstract class OpenFeedDatabase : RoomDatabase() {
    abstract fun openFeedDao(): OpenFeedDao

    companion object {
        @Volatile
        private var INSTANCE: OpenFeedDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'TELEGRAM'")
                db.execSQL("ALTER TABLE channels ADD COLUMN feedUrl TEXT")
                db.execSQL("ALTER TABLE posts ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'TELEGRAM'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE posts ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_articles` (
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `byline` TEXT,
                        `siteName` TEXT,
                        `publishedTime` TEXT,
                        `leadImageUrl` TEXT,
                        `blocksJson` TEXT NOT NULL,
                        `estimatedReadMinutes` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`url`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): OpenFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenFeedDatabase::class.java,
                    "openfeed_cache.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
