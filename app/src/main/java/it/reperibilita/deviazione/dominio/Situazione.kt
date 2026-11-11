package it.reperibilita.deviazione.dominio

import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Preferenze
import it.reperibilita.deviazione.data.Stato
import it.reperibilita.deviazione.data.StatoDeviazione
import it.reperibilita.deviazione.data.Turno

enum class Livello { VERDE, ARANCIONE, ROSSO, GRIGIO }

/** Fotografia completa del momento: chi è di turno, fino a quando, com'è la deviazione. */
data class Situazione(
    val ora: Long,
    val turno: Turno?,
    val collega: Collega?,
    val inizioBlocco: Long?,
    val fineBlocco: Long?,
    val prossimoTurno: Turno?,
    val prossimoCollega: Collega?,
    val prossimoInizio: Long?,
    val fineTurni: Long?,
    val stato: Stato,
    val preferenze: Preferenze,
) {
    val numeroAtteso: String? get() = collega?.telefono
    val deviazioneCorretta: Boolean get() = Nomi.stessoNumero(stato.numero, numeroAtteso)

    val livello: Livello
        get() = when {
            stato.stato == StatoDeviazione.ERRORE -> Livello.ROSSO
            !preferenze.automatico -> Livello.GRIGIO
            stato.stato == StatoDeviazione.CONFERMATA && (turno == null || deviazioneCorretta) -> Livello.VERDE
            stato.stato == StatoDeviazione.IN_CORSO || stato.stato == StatoDeviazione.IN_ATTESA -> Livello.ARANCIONE
            turno != null -> Livello.ARANCIONE
            else -> Livello.GRIGIO
        }

    val titolo: String
        get() = when {
            stato.stato == StatoDeviazione.ERRORE -> "Deviazione NON riuscita"
            !preferenze.automatico ->
                if (stato.stato == StatoDeviazione.DISATTIVATA) "Deviazione disattivata" else "Automatismo sospeso"
            stato.stato == StatoDeviazione.IN_CORSO -> "Deviazione in corso…"
            stato.stato == StatoDeviazione.CONFERMATA && (turno == null || deviazioneCorretta) ->
                "Deviazione attiva e confermata"
            stato.stato == StatoDeviazione.IN_ATTESA -> "In attesa di conferma dell'operatore"
            turno != null -> "Deviazione da aggiornare"
            stato.stato == StatoDeviazione.DISATTIVATA -> "Deviazione disattivata"
            else -> "Nessun turno in corso"
        }

    /** Testo breve per notifica fissa e widget. */
    val riassunto: String
        get() {
            val chi = collega?.cognome ?: turno?.nome
            val fino = fineBlocco?.let { " fino alle ${Formati.ora(it)}" }.orEmpty()
            return if (chi != null) "$chi$fino" else "Nessuno di turno"
        }
}

object CalcolaSituazione {
    fun calcola(turni: List<Turno>, colleghi: List<Collega>, stato: Stato, preferenze: Preferenze, ora: Long): Situazione {
        val numeroDi = { t: Turno -> Nomi.trova(t.nome, colleghi)?.telefono }
        val turno = Calendario.corrente(turni, ora)
        val collega = turno?.let { Nomi.trova(it.nome, colleghi) }
        val fine = Calendario.fineBlocco(turni, ora, numeroDi)
        val inizio = Calendario.inizioBlocco(turni, ora, numeroDi)
        val prossimo = when {
            fine != null -> Calendario.successivo(turni, fine)
            else -> turni.filter { it.inizio > ora }.minByOrNull { it.inizio }
        }
        return Situazione(
            ora = ora,
            turno = turno,
            collega = collega,
            inizioBlocco = inizio,
            fineBlocco = fine,
            prossimoTurno = prossimo,
            prossimoCollega = prossimo?.let { Nomi.trova(it.nome, colleghi) },
            prossimoInizio = if (turno == null) turni.filter { it.inizio > ora }.minOfOrNull { it.inizio } else null,
            fineTurni = turni.maxOfOrNull { it.fine },
            stato = stato,
            preferenze = preferenze,
        )
    }
}
