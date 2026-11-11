package it.reperibilita.deviazione.dominio

import it.reperibilita.deviazione.data.Collega
import java.text.Normalizer
import java.util.Locale

/** Normalizzazione dei nomi, abbinamento Excel ↔ rubrica e formattazione dei numeri. */
object Nomi {

    private val segniDiacritici = Regex("\\p{M}+")
    private val nonAlfanumerici = Regex("[^A-Z0-9 ]")
    private val spazi = Regex("\\s+")

    /** "Cavallari  Francesca" → "CAVALLARI FRANCESCA"; accenti e punteggiatura rimossi. */
    fun normalizza(testo: String): String =
        Normalizer.normalize(testo, Normalizer.Form.NFD)
            .replace(segniDiacritici, "")
            .uppercase(Locale.ROOT)
            .replace(nonAlfanumerici, " ")
            .replace(spazi, " ")
            .trim()

    /** Nome da mostrare per l'Excel: spazi compattati e maiuscolo. */
    fun pulisciNomeExcel(testo: String): String = testo.replace(spazi, " ").trim().uppercase(Locale.ITALIAN)

    /**
     * Cerca in rubrica il collega che corrisponde al nome dell'Excel ("COGNOME NOME").
     * Prima prova con il cognome all'inizio, poi in qualunque posizione.
     */
    fun trova(nomeExcel: String, colleghi: List<Collega>): Collega? {
        val n = normalizza(nomeExcel)
        if (n.isEmpty()) return null
        val iniziali = colleghi.filter { c ->
            val cg = normalizza(c.cognome)
            cg.isNotEmpty() && (n == cg || n.startsWith("$cg "))
        }
        scegli(iniziali, n)?.let { return it }
        val ovunque = colleghi.filter { c ->
            val cg = normalizza(c.cognome)
            cg.isNotEmpty() && " $n ".contains(" $cg ")
        }
        return scegli(ovunque, n)
    }

    private fun scegli(candidati: List<Collega>, n: String): Collega? = when {
        candidati.isEmpty() -> null
        candidati.size == 1 -> candidati.first()
        else -> candidati.firstOrNull { c ->
            val nome = normalizza(c.nome)
            nome.isNotEmpty() && " $n ".contains(" $nome ")
        } ?: candidati.maxBy { normalizza(it.cognome).length }
    }

    /** Proposta di cognome e nome per un nome Excel sconosciuto: prima parola = cognome. */
    fun proponiCognomeNome(nomeExcel: String): Pair<String, String> {
        val parti = pulisciNomeExcel(nomeExcel).split(" ")
        return parti.first() to parti.drop(1).joinToString(" ") { capitalizza(it) }
    }

    fun capitalizza(parola: String): String =
        parola.lowercase(Locale.ITALIAN).split(" ").joinToString(" ") { p ->
            p.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() }
        }

    /** "Francesca Cavallari" se c'è il nome, altrimenti "Cavallari". */
    fun nomeVisualizzato(c: Collega): String =
        listOf(capitalizza(c.nome), capitalizza(c.cognome)).filter { it.isNotBlank() }.joinToString(" ")

    fun iniziali(c: Collega): String {
        val cg = c.cognome.trim()
        val nm = c.nome.trim()
        return when {
            nm.isNotEmpty() && cg.isNotEmpty() -> "${nm.first()}${cg.first()}"
            cg.length >= 2 -> cg.take(2)
            else -> cg.ifEmpty { "?" }
        }.uppercase(Locale.ITALIAN)
    }

    fun inizialiDaNomeExcel(nomeExcel: String): String {
        val parti = pulisciNomeExcel(nomeExcel).split(" ").filter { it.isNotEmpty() }
        return when {
            parti.size >= 2 -> "${parti[1].first()}${parti[0].first()}"
            parti.size == 1 -> parti[0].take(2)
            else -> "?"
        }
    }

    /** Solo cifre (e "+" iniziale) per il codice MMI. */
    fun pulisciTelefono(numero: String): String {
        val t = numero.trim()
        val cifre = t.filter { it.isDigit() }
        return if (t.startsWith("+")) "+$cifre" else cifre
    }

    /** Numero nazionale senza prefisso: "+39 339 283 0941" → "3392830941". */
    fun nazionale(numero: String): String {
        var d = numero.filter { it.isDigit() }
        if (d.startsWith("0039")) d = d.drop(4)
        else if (d.startsWith("39") && d.length >= 11) d = d.drop(2)
        return d
    }

    /** Confronto tollerante: stesso numero nazionale, oppure stesse ultime 9 cifre. */
    fun stessoNumero(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val x = nazionale(a)
        val y = nazionale(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        return x.length >= 9 && y.length >= 9 && x.takeLast(9) == y.takeLast(9)
    }

    /** "3392830941" → "339 283 0941". */
    fun formattaTelefono(numero: String): String {
        val d = nazionale(numero)
        return when {
            d.length == 10 -> "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
            d.length == 9 -> "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
            else -> numero.trim()
        }
    }

    /** Colore stabile per ogni persona (indice nella tavolozza degli avatar). */
    fun indiceColore(chiave: String, quanti: Int): Int {
        val n = normalizza(chiave)
        var h = 7
        for (ch in n) h = h * 31 + ch.code
        return Math.floorMod(h, quanti)
    }
}
