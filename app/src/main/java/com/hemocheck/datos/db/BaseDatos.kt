package com.hemocheck.datos.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        LoteEntidad::class, PaginaEntidad::class, EntradaEntidad::class, ResultadoEntidad::class,
        VerificacionEntidad::class, VerificacionEntradaEntidad::class, CorreccionEntidad::class,
        BorradoPendienteEntidad::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Convertidores::class)
abstract class BaseDatos : RoomDatabase() {
    abstract fun serologia(): SerologiaDao
    abstract fun verificaciones(): VerificacionDao
    abstract fun correcciones(): CorreccionDao
    abstract fun retencion(): RetencionDao

    /** Fuerza que el contenido del registro de escritura (WAL) pase a la base y se trunque. */
    fun consolidar() {
        runCatching { openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
    }

    companion object {
        const val NOMBRE = "hemocheck.db"

        /**
         * Base cifrada con SQLCipher (AES-256). [clave] es la clave aleatoria protegida por
         * Android Keystore. `secure_delete` sobrescribe con ceros el contenido borrado.
         */
        fun abrirCifrada(context: Context, clave: ByteArray): BaseDatos {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BaseDatos::class.java, NOMBRE)
                .openHelperFactory(SupportOpenHelperFactory(clave))
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }
                })
                .build()
        }

        /** Solo para pruebas (sin cifrado, en memoria). */
        fun enMemoria(context: Context): BaseDatos =
            Room.inMemoryDatabaseBuilder(context, BaseDatos::class.java).allowMainThreadQueries().build()
    }
}
