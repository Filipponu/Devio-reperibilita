package it.reperibilita.deviazione.telefonia

import java.text.Normalizer
import java.util.Locale

/** Esito della lettura della risposta dell'operatore a *#21#. */
sealed interface StatoOperatore {
    /** Deviazione attiva verso questi numeri. */
    data class Attiva(val numeri: List<String>) : StatoOperatore
    /** Deviazione non attiva. */
    data object NonAttiva : StatoOperatore
    /** Risposta arrivata ma incomprensibile. */
    data class Sconosciuto(val testo: String) : StatoOperatore
}

/**
 * Interpreta i testi restituiti dall'operatore (o da Android) dopo un codice MMI.
 * I formati cambiano da operatore a operatore, quindi si cercano numeri e parole chiave in modo tollerante.
 */
object InterpreteRisposta {

    private val numero = Regex("(\\+|00)?\\d[\\d \\-]{6,}\\d")

    private val negativeStato = listOf(
        "non attiv", "disattivat", "non e attiv", "non e stato attivat", "not active", "inactive", "disabled",
        "deactivated", "not forwarded", "non inoltrat", "nessun inoltro", "non impostat", "disabilitat", "erased",
        "cancellat", "not registered", "non registrat",
    )
    private val negativeComando = listOf(
        "errore", "error", "non riuscit", "fallit", "failed", "failure", "non consentit", "not allowed",
        "rifiutat", "rejected", "non valid", "invalid", "problema di connessione", "connection problem",
        "non disponibil", "unavailable", "not supported", "non support", "impossibile", "unable",
    )
    private val positiveComando = listOf(
        "attivat", "registrat", "riuscit", "success", "enabled", "activated", "registered", "impostat",
        "abilitat", "eseguit", "completat", "accettat", "erased", "cancellat", "disattivat",
    )

    private fun normalizza(testo: String): String =
        Normalizer.normalize(testo, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    /** Numeri di telefono (8–15 cifre) presenti nel testo, senza duplicati. */
    fun numeri(testo: String): List<String> =
        numero.findAll(testo).map { m ->
            val v = m.value.trim()
            val cifre = v.filter(Char::isDigit)
            if (v.startsWith("+")) "+$cifre" else cifre
        }.filter { it.count(Char::isDigit) in 8..15 }.distinct().toList()

    /** Risposta a *#21#. Se c'è un numero la deviazione è attiva (anche se altri servizi risultano "non attivi"). */
    fun stato(testo: String): StatoOperatore {
        val trovati = numeri(testo)
        if (trovati.isNotEmpty()) return StatoOperatore.Attiva(trovati)
        val t = normalizza(testo)
        if (negativeStato.any { t.contains(it) }) return StatoOperatore.NonAttiva
        return StatoOperatore.Sconosciuto(testo.trim())
    }

    /** Risposta a **21*…# o ##21#: true = riuscito, false = errore, null = non si capisce. */
    fun comandoRiuscito(testo: String): Boolean? {
        val t = normalizza(testo)
        if (negativeComando.any { t.contains(it) }) return false
        if (t.contains("non ") && positiveComando.any { t.contains("non $it") }) return false
        if (positiveComando.any { t.contains(it) }) return true
        return null
    }
}
