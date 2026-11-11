package it.reperibilita.deviazione.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Un foglio Excel: righe e colonne numerate da 1 (A = 1). Contiene solo le celle con un valore. */
class Foglio(val nome: String, val celle: Map<Int, Map<Int, String>>) {
    fun cella(riga: Int, colonna: Int): String? = celle[riga]?.get(colonna)
    val righe: List<Int> get() = celle.keys.sorted()
}

class FileExcelNonValido(messaggio: String, causa: Throwable? = null) : Exception(messaggio, causa)

/**
 * Lettore .xlsx minimo e senza dipendenze: un .xlsx è uno zip di file XML.
 * Legge nomi dei fogli, stringhe condivise e valori delle celle (testo, numeri, stringhe inline,
 * risultati delle formule). Ignora stili, colori e formattazioni.
 */
object XlsxReader {

    private const val LIMITE_VOCE = 20L * 1024 * 1024
    private const val LIMITE_TOTALE = 60L * 1024 * 1024

    fun leggi(input: InputStream): List<Foglio> {
        val buffer = BufferedInputStream(input)
        buffer.mark(8)
        val firma = ByteArray(4)
        val letti = buffer.read(firma)
        buffer.reset()
        if (letti == 4 && firma[0] == 0xD0.toByte() && firma[1] == 0xCF.toByte()) {
            throw FileExcelNonValido(
                "Il file è nel vecchio formato .xls (Excel 97-2003). Aprilo e salvalo come .xlsx, poi riprova."
            )
        }
        if (letti < 2 || firma[0] != 'P'.code.toByte() || firma[1] != 'K'.code.toByte()) {
            throw FileExcelNonValido("Il file non è un Excel .xlsx valido.")
        }

        val voci = HashMap<String, ByteArray>()
        var totale = 0L
        try {
            ZipInputStream(buffer).use { zip ->
                var voce = zip.nextEntry
                while (voce != null) {
                    val nome = voce.name.trimStart('/')
                    if (!voce.isDirectory && (nome.endsWith(".xml") || nome.endsWith(".rels"))) {
                        val dati = leggiConLimite(zip)
                        totale += dati.size
                        if (totale > LIMITE_TOTALE) throw FileExcelNonValido("Il file è troppo grande.")
                        voci[nome] = dati
                    }
                    voce = zip.nextEntry
                }
            }
        } catch (e: FileExcelNonValido) {
            throw e
        } catch (e: Exception) {
            throw FileExcelNonValido("Impossibile aprire il file Excel: ${e.message}", e)
        }

        val workbook = voci["xl/workbook.xml"] ?: throw FileExcelNonValido("Il file non è un Excel .xlsx valido.")
        val stringhe = voci["xl/sharedStrings.xml"]?.let(::leggiStringheCondivise) ?: emptyList()
        val relazioni = voci["xl/_rels/workbook.xml.rels"]?.let(::leggiRelazioni) ?: emptyMap()

        return leggiElencoFogli(workbook).mapIndexedNotNull { indice, (nome, rid) ->
            val destinazione = relazioni[rid] ?: "worksheets/sheet${indice + 1}.xml"
            val dati = voci[percorso(destinazione)] ?: return@mapIndexedNotNull null
            Foglio(nome, leggiFoglio(dati, stringhe))
        }
    }

    private fun leggiConLimite(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var tot = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            tot += n
            if (tot > LIMITE_VOCE) throw FileExcelNonValido("Il file è troppo grande.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun percorso(destinazione: String): String {
        val d = destinazione.replace('\\', '/')
        return when {
            d.startsWith("/") -> d.trimStart('/')
            d.startsWith("xl/") -> d
            else -> "xl/" + d.removePrefix("./")
        }
    }

    private fun analizza(dati: ByteArray, gestore: DefaultHandler) {
        val fabbrica = SAXParserFactory.newInstance()
        fabbrica.isNamespaceAware = true
        runCatching { fabbrica.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { fabbrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        fabbrica.newSAXParser().parse(ByteArrayInputStream(dati), gestore)
    }

    private fun nome(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

    private fun attributo(attr: Attributes, nomeLocale: String): String? {
        for (i in 0 until attr.length) {
            if (nome(attr.getLocalName(i), attr.getQName(i)) == nomeLocale) return attr.getValue(i)
        }
        return null
    }

    private fun leggiStringheCondivise(dati: ByteArray): List<String> {
        val risultato = ArrayList<String>()
        analizza(dati, object : DefaultHandler() {
            var inSi = false
            var inFonetica = 0
            var inT = false
            val sb = StringBuilder()
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when (nome(localName, qName)) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "rPh" -> inFonetica++
                    "t" -> if (inSi && inFonetica == 0) inT = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (nome(localName, qName)) {
                    "si" -> { inSi = false; risultato.add(sb.toString()) }
                    "rPh" -> inFonetica--
                    "t" -> inT = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) sb.appendRange(ch, start, start + length)
            }
        })
        return risultato
    }

    private fun leggiRelazioni(dati: ByteArray): Map<String, String> {
        val mappa = HashMap<String, String>()
        analizza(dati, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (nome(localName, qName) == "Relationship") {
                    val id = attributo(attributes, "Id")
                    val target = attributo(attributes, "Target")
                    if (id != null && target != null) mappa[id] = target
                }
            }
        })
        return mappa
    }

    private fun leggiElencoFogli(dati: ByteArray): List<Pair<String, String>> {
        val fogli = ArrayList<Pair<String, String>>()
        analizza(dati, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (nome(localName, qName) == "sheet") {
                    val nomeFoglio = attributo(attributes, "name") ?: "Foglio ${fogli.size + 1}"
                    val rid = attributo(attributes, "id").orEmpty()
                    fogli.add(nomeFoglio to rid)
                }
            }
        })
        return fogli
    }

    private val rifCella = Regex("^([A-Za-z]+)(\\d+)$")

    private fun colonna(lettere: String): Int {
        var n = 0
        for (c in lettere.uppercase()) n = n * 26 + (c - 'A' + 1)
        return n
    }

    private fun leggiFoglio(dati: ByteArray, stringhe: List<String>): Map<Int, Map<Int, String>> {
        val righe = HashMap<Int, HashMap<Int, String>>()
        analizza(dati, object : DefaultHandler() {
            var riga = 0
            var col = 0
            var tipo: String? = null
            var inV = false
            var inIs = false
            var inT = false
            val valore = StringBuilder()
            val inline = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                when (nome(localName, qName)) {
                    "row" -> {
                        riga = attributo(attributes, "r")?.toIntOrNull() ?: (riga + 1)
                        col = 0
                    }
                    "c" -> {
                        val rif = attributo(attributes, "r")?.let { rifCella.find(it) }
                        if (rif != null) {
                            col = colonna(rif.groupValues[1])
                            riga = rif.groupValues[2].toInt()
                        } else {
                            col += 1
                        }
                        tipo = attributo(attributes, "t")
                        valore.setLength(0)
                        inline.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inT = true
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (nome(localName, qName)) {
                    "v" -> inV = false
                    "is" -> inIs = false
                    "t" -> inT = false
                    "c" -> {
                        val testo = when (tipo) {
                            "s" -> valore.toString().trim().toIntOrNull()?.let { stringhe.getOrNull(it) }
                            "inlineStr" -> inline.toString()
                            "b" -> if (valore.toString().trim() == "1") "TRUE" else "FALSE"
                            "e" -> null
                            else -> valore.toString().takeIf { it.isNotEmpty() }
                        }
                        if (!testo.isNullOrEmpty()) righe.getOrPut(riga) { HashMap() }[col] = testo
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV) valore.appendRange(ch, start, start + length)
                if (inT) inline.appendRange(ch, start, start + length)
            }
        })
        return righe
    }
}
