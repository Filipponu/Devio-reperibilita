package it.reperibilita.deviazione.excel

import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.TipoTurno
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Risultato della lettura di un foglio mensile. */
data class FoglioTurni(
    val nomeFoglio: String,
    val mese: YearMonth?,
    val turni: List<Turno>,
    /** Valori presenti nelle celle ma non riconosciuti come turno (es. CORSO, LIBERO) → quante volte. */
    val codiciIgnorati: Map<String, Int>,
    val errore: String? = null,
)

/**
 * Converte i fogli Excel in turni. Struttura attesa (come il file "REPERIBILITà 2026"):
 * una riga di intestazione con i giorni 1…31, una colonna a sinistra con "COGNOME NOME",
 * una riga per infermiere, codici G/N/24H (anche GD/ND/24HD per i festivi). La lettura si ferma alla "LEGENDA".
 */
object LettoreTurni {

    private val MESI = mapOf(
        "GENNAIO" to 1, "FEBBRAIO" to 2, "MARZO" to 3, "APRILE" to 4, "MAGGIO" to 5, "GIUGNO" to 6,
        "LUGLIO" to 7, "AGOSTO" to 8, "SETTEMBRE" to 9, "OTTOBRE" to 10, "NOVEMBRE" to 11, "DICEMBRE" to 12,
        "GEN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAG" to 5, "GIU" to 6,
        "LUG" to 7, "AGO" to 8, "SET" to 9, "OTT" to 10, "NOV" to 11, "DIC" to 12,
    )
    private val LETTERE_SETTIMANA = setOf("L", "M", "G", "V", "S", "D")
    private val anno = Regex("\\b(20\\d{2})\\b")

    fun leggi(fogli: List<Foglio>): List<FoglioTurni> = fogli.map(::leggiFoglio)

    /** "NOVEMBRE 2026", "Reperibilità novembre 2026", "NOV 26"... */
    fun meseDaTesto(testo: String): YearMonth? {
        val t = Nomi.normalizza(testo)
        val parole = t.split(" ")
        val mese = parole.firstNotNullOfOrNull { MESI[it] } ?: return null
        val a = anno.find(t)?.groupValues?.get(1)?.toInt()
            ?: parole.firstOrNull { it.length == 2 && it.all(Char::isDigit) }?.let { 2000 + it.toInt() }
            ?: return null
        return YearMonth.of(a, mese)
    }

    private class Intestazione(val riga: Int, val giorni: Map<Int, Int>, val meseDaDate: YearMonth?)

    private fun numeroIntero(valore: String): Int? {
        val d = valore.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val i = d.toInt()
        return if (d == i.toDouble()) i else null
    }

    private fun trovaIntestazione(foglio: Foglio): Intestazione? {
        for (riga in foglio.righe) {
            val celle = foglio.celle[riga].orEmpty().toSortedMap()
            // Caso 1: numeri 1, 2, 3 … consecutivi da sinistra a destra.
            val giorni = LinkedHashMap<Int, Int>()
            var atteso = 1
            for ((col, v) in celle) {
                val n = numeroIntero(v) ?: continue
                if (n == atteso) {
                    giorni[col] = n
                    atteso++
                } else if (n == 1) {
                    giorni.clear(); giorni[col] = 1; atteso = 2
                }
            }
            if (giorni.size >= 28) return Intestazione(riga, giorni, null)

            // Caso 2: date vere (numeri seriali di Excel) mostrate come giorno.
            val date = celle.mapNotNull { (col, v) ->
                val n = numeroIntero(v) ?: return@mapNotNull null
                if (n in 35000..80000) col to LocalDate.of(1899, 12, 30).plusDays(n.toLong()) else null
            }
            if (date.size >= 28) {
                val primo = date.first().second
                val consecutive = date.withIndex().all { (i, p) -> p.second == primo.plusDays(i.toLong()) }
                if (consecutive) {
                    return Intestazione(riga, date.associate { it.first to it.second.dayOfMonth }, YearMonth.from(primo))
                }
            }
        }
        return null
    }

    fun leggiFoglio(foglio: Foglio): FoglioTurni {
        val intestazione = trovaIntestazione(foglio)
            ?: return FoglioTurni(foglio.nome, meseDaTesto(foglio.nome), emptyList(), emptyMap(),
                "Non trovo la riga con i giorni del mese (1, 2, 3…).")

        val mese = meseDaTesto(foglio.nome)
            ?: foglio.righe.filter { it <= intestazione.riga }
                .flatMap { foglio.celle[it].orEmpty().values }
                .firstNotNullOfOrNull { meseDaTesto(it) }
            ?: intestazione.meseDaDate
            ?: return FoglioTurni(foglio.nome, null, emptyList(), emptyMap(),
                "Non riesco a capire mese e anno (il nome del foglio dovrebbe essere tipo \"NOVEMBRE 2026\").")

        val primaColonnaGiorni = intestazione.giorni.keys.min()
        val turni = ArrayList<Turno>()
        val ignorati = LinkedHashMap<String, Int>()

        for (riga in foglio.righe) {
            if (riga <= intestazione.riga) continue
            val celle = foglio.celle[riga].orEmpty()
            val nomeGrezzo = celle.filterKeys { it < primaColonnaGiorni }
                .toSortedMap().values.lastOrNull { it.isNotBlank() } ?: continue
            val nomeNorm = Nomi.normalizza(nomeGrezzo)
            if (nomeNorm.startsWith("LEGENDA")) break
            if (nomeNorm.isEmpty() || nomeNorm.any(Char::isDigit)) continue

            val valori = intestazione.giorni.keys.mapNotNull { celle[it]?.trim()?.takeIf(String::isNotEmpty) }
            // Riga dei giorni della settimana (L M M G V S D) con un'etichetta: non è un infermiere.
            if (valori.size >= 20 && valori.all { it.uppercase(Locale.ROOT) in LETTERE_SETTIMANA }) continue

            val nome = Nomi.pulisciNomeExcel(nomeGrezzo)
            for ((col, giorno) in intestazione.giorni) {
                val grezzo = celle[col]?.trim().orEmpty()
                if (grezzo.isEmpty()) continue
                val codice = TipoTurno.normalizzaCodice(grezzo)
                val tipo = TipoTurno.daCodice(codice)
                if (tipo == null) {
                    ignorati[codice] = (ignorati[codice] ?: 0) + 1
                    continue
                }
                if (giorno > mese.lengthOfMonth()) continue
                val (inizio, fine) = tipo.intervallo(mese.atDay(giorno))
                turni += Turno(
                    inizio = inizio.toInstant().toEpochMilli(),
                    fine = fine.toInstant().toEpochMilli(),
                    nome = nome,
                    codice = codice,
                    mese = mese.toString(),
                )
            }
        }
        return FoglioTurni(foglio.nome, mese, turni.sortedBy { it.inizio }, ignorati)
    }
}
