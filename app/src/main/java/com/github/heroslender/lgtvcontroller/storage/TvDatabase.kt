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
@Database(entities = [TvEntity::class], version = 5, exportSchema = false)
abstract class TvDatabase: RoomDatabase() {

    companion object {
        @Volatile
        private var Instance: TvDatabase? = null

        /** Adds the TV MAC address, used for Wake-on-LAN. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tvs ADD COLUMN macAddress TEXT")
            }
        }

        fun getDatabase(context: Context): TvDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, TvDatabase::class.java, "tv_database")
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { Instance = it }
            }
        }
    }

    abstract fun tvDao(): TvDao
}