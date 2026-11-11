package it.reperibilita.deviazione.excel

import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Nomi

/** Un foglio del file con quello che succederà importandolo. */
data class MeseInAnteprima(
    val foglio: FoglioTurni,
    /** Tutti i turni del foglio sono già finiti: non viene importato. */
    val passato: Boolean,
) {
    val daImportare: Boolean get() = !passato && foglio.errore == null && foglio.turni.isNotEmpty()
}

/** Anteprima di un'importazione: cosa si salva e quali problemi ci sono. */
data class Anteprima(
    val nomeFile: String,
    val mesi: List<MeseInAnteprima>,
    val turniDaSalvare: List<Turno>,
    val mesiDaSostituire: List<String>,
    val analisi: Analisi,
    /** Nomi dei turni futuri che non sono in rubrica. */
    val nomiSconosciuti: List<String>,
    /** Cambi manuali futuri nei mesi che verranno reimportati. */
    val cambiManuali: List<Turno> = emptyList(),
) {
    val niente: Boolean get() = turniDaSalvare.isEmpty()
}

object PreparaAnteprima {
    fun prepara(nomeFile: String, fogli: List<FoglioTurni>, esistenti: List<Turno>, colleghi: List<Collega>, ora: Long): Anteprima {
        val mesi = fogli.map { f -> MeseInAnteprima(f, passato = f.turni.isNotEmpty() && f.turni.all { it.fine <= ora }) }
        val daImportare = mesi.filter { it.daImportare }
        val chiavi = daImportare.mapNotNull { it.foglio.mese?.toString() }.distinct()
        val turni = daImportare.flatMap { it.foglio.turni }.sortedBy { it.inizio }
        val unione = esistenti.filter { it.manuale || it.mese !in chiavi } + turni
        return Anteprima(
            nomeFile = nomeFile,
            mesi = mesi,
            turniDaSalvare = turni,
            mesiDaSostituire = chiavi,
            analisi = Validatore.analizzaConManuali(unione, ora),
            nomiSconosciuti = sconosciuti(turni, colleghi, ora),
            cambiManuali = esistenti.filter { it.manuale && it.mese in chiavi && it.fine > ora }.sortedBy { it.inizio },
        )
    }

    fun sconosciuti(turni: List<Turno>, colleghi: List<Collega>, ora: Long): List<String> =
        turni.filter { it.fine > ora }.map { it.nome }.distinct().filter { Nomi.trova(it, colleghi) == null }
}
