package com.github.heroslender.lgtvcontroller.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.heroslender.lgtvcontroller.storage.entity.TvEntity

@TypeConverters(value = [RoomTypeConverters::class])
@Database(entities = [TvEntity::class], version = 8, exportSchema = false)
abstract class TvDatabase: RoomDatabase() {

    companion object {
        @Volatile
        private var Instance: TvDatabase? = null

        /** Adds the TV's last known address and UUID, used to connect without discovery. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tvs ADD COLUMN ipAddress TEXT")
                db.execSQL("ALTER TABLE tvs ADD COLUMN serviceUuid TEXT")
            }
        }

        /** Adds the action of a long press on the OK button. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tvs ADD COLUMN okLongPressAction TEXT")
            }
        }

        /** Adds the app launched by the Home button. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tvs ADD COLUMN homeAppId TEXT")
            }
        }

        /** Adds the TV MAC address, used for Wake-on-LAN. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tvs ADD COLUMN macAddress TEXT")
            }
        }

        fun getDatabase(context: Context): TvDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, TvDatabase::class.java, "tv_database")
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { Instance = it }
            }
        }
    }

    abstract fun tvDao(): TvDao
}