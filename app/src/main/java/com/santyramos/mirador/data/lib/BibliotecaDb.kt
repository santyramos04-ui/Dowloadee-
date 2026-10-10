package com.santyramos.mirador.data.lib

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos de la biblioteca personal (suscripciones, historial, ver más tarde, listas).
 * Está separada de la de descargas para no tocar nada de lo que ya funciona.
 */
@Database(
    entities = [Suscripcion::class, Novedad::class, Visto::class, Guardado::class, ListaPropia::class, ItemLista::class],
    version = 1, exportSchema = false,
)
abstract class BibliotecaDb : RoomDatabase() {
    abstract fun dao(): BibliotecaDao

    companion object {
        @Volatile private var instancia: BibliotecaDb? = null

        fun get(context: Context): BibliotecaDb = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(context.applicationContext, BibliotecaDb::class.java, "biblioteca.db")
                // Al cambiar las tablas se escriben migraciones aquí: NUNCA borrar datos del usuario.
                .build().also { instancia = it }
        }
    }
}
