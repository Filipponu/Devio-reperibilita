package it.reperibilita.deviazione.excel

import it.reperibilita.deviazione.data.Turno

data class Buco(val inizio: Long, val fine: Long)

data class Sovrapposizione(val primo: Turno, val secondo: Turno, val inizio: Long, val fine: Long)

data class Analisi(
    val buchi: List<Buco>,
    val sovrapposizioni: List<Sovrapposizione>,
    /** Fino a quando la reperibilità è coperta senza interruzioni (o fine dell'ultimo turno). */
    val fineTurni: Long?,
)

/** Controlla buchi e sovrapposizioni nei turni che finiscono dopo [da]. */
object Validatore {
    /**
     * Come [analizza], ma i cambi manuali coprono i buchi senza contare come sovrapposizioni
     * (per definizione si sovrappongono al turno che sostituiscono).
     */
    fun analizzaConManuali(turni: List<Turno>, da: Long): Analisi {
        val tutti = analizza(turni, da)
        val soloExcel = analizza(turni.filter { !it.manuale }, da)
        return Analisi(tutti.buchi, soloExcel.sovrapposizioni, tutti.fineTurni)
    }

    fun analizza(turni: List<Turno>, da: Long): Analisi {
        val rilevanti = turni.filter { it.fine > da }.sortedWith(compareBy({ it.inizio }, { it.fine }))
        if (rilevanti.isEmpty()) return Analisi(emptyList(), emptyList(), null)
        val buchi = ArrayList<Buco>()
        val sovrapposizioni = ArrayList<Sovrapposizione>()
        var coperto = da
        var ultimo: Turno? = null
        for (t in rilevanti) {
            val precedente = ultimo
            if (t.inizio > coperto) {
                buchi += Buco(coperto, t.inizio)
            } else if (precedente != null && t.inizio < precedente.fine) {
                sovrapposizioni += Sovrapposizione(precedente, t, t.inizio, minOf(precedente.fine, t.fine))
            }
            if (precedente == null || t.fine > precedente.fine) ultimo = t
            coperto = maxOf(coperto, t.fine)
        }
        return Analisi(buchi, sovrapposizioni, coperto)
    }
}
