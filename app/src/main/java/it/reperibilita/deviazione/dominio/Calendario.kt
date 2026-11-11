package it.reperibilita.deviazione.dominio

import it.reperibilita.deviazione.data.Turno

/** Calcoli sul calendario dei turni: chi è di turno ora, quando cambia, chi viene dopo. */
object Calendario {

    /** Chi vince quando due turni si sovrappongono: prima i cambi manuali, poi chi ha iniziato più di recente. */
    private val precedenza = compareBy<Turno>({ it.manuale }, { it.inizio }, { it.id })

    /** Turno effettivo all'istante [ora] (cambi manuali prima dei turni Excel). */
    fun corrente(turni: List<Turno>, ora: Long): Turno? =
        turni.filter { it.inizio <= ora && ora < it.fine }.maxWithOrNull(precedenza)

    /** Tutti gli istanti in cui inizia o finisce un turno, in ordine. */
    private fun confini(turni: List<Turno>): List<Long> =
        turni.flatMap { listOf(it.inizio, it.fine) }.distinct().sorted()

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
        turni.filter { it.fine > ora }.sortedWith(compareBy({ it.inizio }, { !it.manuale })).take(quanti)

    private fun stessoNumero(a: Turno?, numero: String?, numeroDi: (Turno) -> String?): Boolean =
        a != null && numero != null && Nomi.stessoNumero(numeroDi(a), numero)

    /**
     * Fino a quando resta la stessa persona (stesso numero) di turno a partire da [ora],
     * anche attraverso più turni consecutivi. Serve per "fino alle HH:MM" e per il conto alla rovescia.
     */
    fun fineBlocco(turni: List<Turno>, ora: Long, numeroDi: (Turno) -> String?): Long? {
        val attuale = corrente(turni, ora) ?: return null
        val numero = numeroDi(attuale)
        for (confine in confini(turni)) {
            if (confine <= ora) continue
            if (!stessoNumero(corrente(turni, confine), numero, numeroDi)) return confine
        }
        return attuale.fine
    }

    /** Da quando la stessa persona è di turno, tornando indietro da [ora]. */
    fun inizioBlocco(turni: List<Turno>, ora: Long, numeroDi: (Turno) -> String?): Long? {
        val attuale = corrente(turni, ora) ?: return null
        val numero = numeroDi(attuale)
        var inizio = attuale.inizio
        for (confine in confini(turni).filter { it <= ora }.asReversed()) {
            inizio = confine
            if (!stessoNumero(corrente(turni, confine - 1), numero, numeroDi)) break
        }
        return inizio
    }

    /** Il turno effettivo all'istante [dopo] (chi subentra alla fine del blocco attuale). */
    fun successivo(turni: List<Turno>, dopo: Long): Turno? = corrente(turni, dopo)

    /** Il cambio manuale che copre per intero il turno Excel [t], se esiste. */
    fun sostituitoDa(turni: List<Turno>, t: Turno): Turno? =
        if (t.manuale) null
        else turni.filter { it.manuale && it.inizio <= t.inizio && it.fine >= t.fine }.maxWithOrNull(precedenza)

    /** Cambi manuali che coprono solo una parte del turno Excel [t]. */
    fun cambiParziali(turni: List<Turno>, t: Turno): List<Turno> =
        if (t.manuale) emptyList()
        else turni.filter { it.manuale && it.inizio < t.fine && it.fine > t.inizio && !(it.inizio <= t.inizio && it.fine >= t.fine) }
}
