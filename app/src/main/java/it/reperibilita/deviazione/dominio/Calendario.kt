package it.reperibilita.deviazione.dominio

import it.reperibilita.deviazione.data.Turno

/** Calcoli sul calendario dei turni: chi è di turno ora, quando cambia, chi viene dopo. */
object Calendario {

    /** Turno in corso all'istante [ora]. Se due turni si sovrappongono vince quello iniziato più di recente. */
    fun corrente(turni: List<Turno>, ora: Long): Turno? =
        turni.filter { it.inizio <= ora && ora < it.fine }
            .maxWithOrNull(compareBy<Turno>({ it.inizio }, { it.id }))

    /** Prossimo istante in cui inizia o finisce un turno (quando va ricontrollata la deviazione). */
    fun prossimoConfine(turni: List<Turno>, ora: Long): Long? {
        var minimo: Long? = null
        for (t in turni) {
            if (t.inizio > ora && (minimo == null || t.inizio < minimo)) minimo = t.inizio
            if (t.fine > ora && (minimo == null || t.fine < minimo)) minimo = t.fine
        }
        return minimo
    }

    /** Turni non ancora finiti, in ordine di inizio. */
    fun prossimi(turni: List<Turno>, ora: Long, quanti: Int = Int.MAX_VALUE): List<Turno> =
        turni.filter { it.fine > ora }.sortedBy { it.inizio }.take(quanti)

    /**
     * Fine del "blocco" del turno [attuale]: se il turno seguente inizia subito dopo ed è della stessa persona
     * (stesso numero), il blocco continua. Serve per "fino alle HH:MM" e per il conto alla rovescia.
     */
    fun fineBlocco(turni: List<Turno>, attuale: Turno, numeroDi: (Turno) -> String?): Long {
        val numero = numeroDi(attuale)
        var fine = attuale.fine
        val ordinati = turni.sortedBy { it.inizio }
        var cambiato = true
        while (cambiato) {
            cambiato = false
            val seguente = ordinati.filter { it.inizio <= fine && it.fine > fine }
                .maxWithOrNull(compareBy<Turno>({ it.inizio }, { it.id })) ?: break
            if (numero != null && Nomi.stessoNumero(numeroDi(seguente), numero)) {
                fine = seguente.fine
                cambiato = true
            }
        }
        return fine
    }

    /** Il turno che subentra alla fine del blocco attuale (se esiste). */
    fun successivo(turni: List<Turno>, dopo: Long): Turno? = corrente(turni, dopo)
}
