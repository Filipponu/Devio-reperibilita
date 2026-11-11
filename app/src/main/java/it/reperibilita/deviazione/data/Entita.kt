package it.reperibilita.deviazione.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un turno di reperibilità letto dall'Excel. Orari in millisecondi (epoch), fuso Europe/Rome già applicato. */
@Entity(tableName = "turni", indices = [Index("inizio"), Index("mese")])
data class Turno(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inizio: Long,
    val fine: Long,
    /** Nome come compare nell'Excel, es. "NUCERA FILIPPO". */
    val nome: String,
    /** Codice originale normalizzato: G, GD, N, ND, 24H, 24HD. */
    val codice: String,
    /** Mese del foglio di provenienza, formato "2026-11". */
    val mese: String,
)

/** Un collega della rubrica. */
@Entity(tableName = "colleghi")
data class Collega(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cognome: String,
    val nome: String = "",
    val telefono: String,
)

enum class TipoEvento { ATTIVAZIONE, DISATTIVAZIONE, VERIFICA, PROVA_CHIAMATA, IMPORTAZIONE, ALLARME, INFO }

enum class Esito { CONFERMATA, NON_VERIFICATA, ERRORE, RIUSCITA, NON_RIUSCITA, INFO }

/** Una riga del registro storico. */
@Entity(tableName = "registro", indices = [Index("quando")])
data class EventoRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quando: Long,
    val tipo: TipoEvento,
    val esito: Esito,
    val numero: String? = null,
    val nome: String? = null,
    val dettaglio: String? = null,
)
