package com.santyramos.mirador.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun descargas(): DownloadDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun get(context: Context): AppDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mirador.db")
                // Cuando agreguemos tablas se añaden migraciones aquí: NUNCA borrar datos del usuario.
                .build().also { instancia = it }
        }
    }
}
