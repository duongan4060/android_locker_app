package com.example.locker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database khoi tao va quan ly Instance locker_db.
 */
@Database(
    entities = [
        LockerEntity::class,
        LockerSessionEntity::class,
        SystemConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LockerDatabase : RoomDatabase() {

    abstract fun lockerDao(): LockerDao

    companion object {
        @Volatile
        private var INSTANCE: LockerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lockers_new` (
                        `lockerId` INTEGER NOT NULL,
                        `lockerCode` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `encryptedEmbedding` BLOB,
                        `iv` BLOB,
                        `currentUserId` TEXT,
                        `sessionStartAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncState` INTEGER NOT NULL,
                        PRIMARY KEY(`lockerId`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `lockers_new` (`lockerId`, `lockerCode`, `status`, `encryptedEmbedding`, `iv`, `currentUserId`, `sessionStartAt`, `createdAt`, `updatedAt`, `syncState`)
                    SELECT 
                        `lockerId`,
                        'LOCKER-' || PRINTF('%03d', `lockerId`),
                        'OCCUPIED',
                        `encryptedEmbedding`,
                        `iv`,
                        'USR-MIGRATED-' || `lockerId`,
                        `timestamp`,
                        `timestamp`,
                        `timestamp`,
                        0
                    FROM `lockers`
                """.trimIndent())

                db.execSQL("DROP TABLE `lockers` ")
                db.execSQL("ALTER TABLE `lockers_new` RENAME TO `lockers` ")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lockers_lockerCode` ON `lockers` (`lockerCode`) ")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `locker_sessions` (
                        `sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `lockerId` INTEGER NOT NULL,
                        `lockerCode` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER,
                        `status` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncState` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locker_sessions_lockerId` ON `locker_sessions` (`lockerId`) ")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locker_sessions_lockerCode` ON `locker_sessions` (`lockerCode`) ")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_locker_sessions_startTime` ON `locker_sessions` (`startTime`) ")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `system_config` (
                        `configKey` TEXT NOT NULL,
                        `configValue` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`configKey`)
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): LockerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LockerDatabase::class.java,
                    "locker_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

