package com.lw.top.lib_core.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lw.top.lib_core.data.local.dao.AiAssistantDao
import com.lw.top.lib_core.data.local.dao.MediaFilesDao
import com.lw.top.lib_core.data.local.dao.TranslationDao
import com.lw.top.lib_core.data.local.dao.UsersDao
import com.lw.top.lib_core.data.local.entity.AiAssistantEntity
import com.lw.top.lib_core.data.local.entity.MediaFilesEntity
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity
import com.lw.top.lib_core.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MediaFilesEntity::class,
        AiAssistantEntity::class,
        TranslationSessionEntity::class,
        TranslationMessageEntity::class
    ],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UsersDao
    abstract fun mediaFilesDao(): MediaFilesDao
    abstract fun aiAssistantDao(): AiAssistantDao
    abstract fun translationDao(): TranslationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_assistant` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `questionType` TEXT NOT NULL, `answerType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE translation_sessions ADD COLUMN translationMode TEXT NOT NULL DEFAULT '${TranslationSessionEntity.MODE_REAL_TIME}'",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE ai_assistant ADD COLUMN answerAudioPath TEXT",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE ai_assistant ADD COLUMN messageId TEXT",
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
                .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration() // 结构变化较大，开发阶段使用破坏性迁移
                .build()
        }
    }
}
