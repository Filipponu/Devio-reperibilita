package it.reperibilita.deviazione.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TurniDao {
    @Query("SELECT * FROM turni ORDER BY inizio, id")
    fun osserva(): Flow<List<Turno>>

    @Query("SELECT * FROM turni ORDER BY inizio, id")
    suspend fun tutti(): List<Turno>

    /** Turni dell'Excel dei mesi indicati (i cambi manuali restano). */
    @Query("DELETE FROM turni WHERE mese IN (:mesi) AND manuale = 0")
    suspend fun eliminaMesiExcel(mesi: List<String>)

    @Query("DELETE FROM turni WHERE mese IN (:mesi) AND manuale = 1")
    suspend fun eliminaManualiDeiMesi(mesi: List<String>)

    @Query("DELETE FROM turni WHERE fine < :prima")
    suspend fun eliminaPrimaDi(prima: Long)

    @Insert
    suspend fun inserisci(turni: List<Turno>)

    @Insert
    suspend fun inserisciUno(turno: Turno): Long

    @Delete
    suspend fun elimina(turno: Turno)

    /**
     * Sostituisce i turni Excel dei mesi importati, lasciando intatti gli altri mesi.
     * I cambi manuali restano, a meno che [eliminaManuali] sia true.
     */
    @Transaction
    suspend fun sostituisciMesi(mesi: List<String>, turni: List<Turno>, eliminaManuali: Boolean = false) {
        eliminaMesiExcel(mesi)
        if (eliminaManuali) eliminaManualiDeiMesi(mesi)
        inserisci(turni)
    }
}

@Dao
interface ColleghiDao {
    @Query("SELECT * FROM colleghi ORDER BY cognome COLLATE NOCASE, nome COLLATE NOCASE")
    fun osserva(): Flow<List<Collega>>

    @Query("SELECT * FROM colleghi ORDER BY cognome COLLATE NOCASE, nome COLLATE NOCASE")
    suspend fun tutti(): List<Collega>

    @Upsert
    suspend fun salva(collega: Collega): Long

    @Delete
    suspend fun elimina(collega: Collega)
}

@Dao
interface RegistroDao {
    @Query("SELECT * FROM registro ORDER BY quando DESC, id DESC LIMIT :limite")
    fun osserva(limite: Int): Flow<List<EventoRegistro>>

    @Insert
    suspend fun aggiungi(evento: EventoRegistro)

    @Query("DELETE FROM registro WHERE quando < :prima")
    suspend fun eliminaPrimaDi(prima: Long)
}

@Database(entities = [Turno::class, Collega::class, EventoRegistro::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun turni(): TurniDao
    abstract fun colleghi(): ColleghiDao
    abstract fun registro(): RegistroDao

    companion object {
        /** Rubrica precaricata al primo avvio (cognome come nell'Excel). */
        val RUBRICA_INIZIALE = listOf(
            Collega(cognome = "CAVALLARI", nome = "Francesca", telefono = "3403718061"),
            Collega(cognome = "SOLA", nome = "Giada", telefono = "3423978822"),
            Collega(cognome = "BINJAKU", nome = "Ermir", telefono = "3208783328"),
            Collega(cognome = "FORNI", nome = "Jana", telefono = "3402505457"),
            Collega(cognome = "NUCERA", nome = "Filippo", telefono = "3392830941"),
        )

        /** Versione 2: cambi turno manuali. */
        val MIGRAZIONE_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE turni ADD COLUMN manuale INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun crea(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "deviazione.db")
                .addMigrations(MIGRAZIONE_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        RUBRICA_INIZIALE.forEach { c ->
                            db.execSQL(
                                "INSERT INTO colleghi (cognome, nome, telefono) VALUES (?, ?, ?)",
                                arrayOf(c.cognome, c.nome, c.telefono),
                            )
                        }
                    }
                })
                .build()
    }
}
