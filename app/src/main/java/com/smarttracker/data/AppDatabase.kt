package com.smarttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao

    companion object {
        const val NAME = "smart_tracker.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }

        /** Used by restore: closes the connection so the DB file can be replaced. */
        fun closeAndReset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
