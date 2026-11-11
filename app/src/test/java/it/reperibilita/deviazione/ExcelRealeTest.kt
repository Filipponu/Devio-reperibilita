package it.reperibilita.deviazione

import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.ZONA_ROMA
import it.reperibilita.deviazione.excel.LettoreTurni
import it.reperibilita.deviazione.excel.Validatore
import it.reperibilita.deviazione.excel.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth

/** Prove sul file reale "REPERIBILITà 2026.xlsx" (gennaio–novembre 2026). */
class ExcelRealeTest {

    private val fogli by lazy {
        javaClass.classLoader!!.getResourceAsStream("reperibilita-2026.xlsx").use { XlsxReader.leggi(it) }
    }
    private val letti by lazy { LettoreTurni.leggi(fogli) }

    private fun millis(s: String) = LocalDateTime.parse(s).atZone(ZONA_ROMA).toInstant().toEpochMilli()

    @Test
    fun leggeTuttiIFogli() {
        assertEquals(11, fogli.size)
        assertEquals("GENNAIO 2026", fogli.first().nome)
        assertEquals("NOVEMBRE 2026", fogli.last().nome)
        val nov = fogli.last()
        assertEquals("REPERIBILITA' NOVEMBRE 2026", nov.cella(1, 1))
        assertEquals("1", nov.cella(2, 2))
        assertEquals("BINJAKU ERMIR", nov.cella(4, 1))
    }

    @Test
    fun turniDiNovembre() {
        val nov = letti.last()
        assertNull(nov.errore)
        assertEquals(YearMonth.of(2026, 11), nov.mese)
        assertEquals(51, nov.turni.size)
        val primo = nov.turni.first()
        assertEquals("FORNI JANA", primo.nome)
        assertEquals("24HD", primo.codice)
        assertEquals(millis("2026-11-01T08:00"), primo.inizio)
        assertEquals(millis("2026-11-02T08:00"), primo.fine)
        // 2 novembre: BINJAKU di giorno, NUCERA di notte
        val due = nov.turni.filter { Formati.zdt(it.inizio).dayOfMonth == 2 }
        assertEquals(listOf("BINJAKU ERMIR" to "G", "NUCERA FILIPPO" to "N"), due.map { it.nome to it.codice })
        val ultimo = nov.turni.last()
        assertEquals(millis("2026-12-01T08:00"), ultimo.fine)
    }

    @Test
    fun ignoraLegendaERigaDeiGiorni() {
        val nomi = letti.flatMap { f -> f.turni.map { it.nome } }.toSet()
        assertEquals(
            setOf("CAVALLARI FRANCESCA", "NUCERA FILIPPO", "PUGLIESE CARMELA", "SOLA GIADA", "ESPOSITO CHIARA",
                "BINJAKU ERMIR", "FORNI JANA"),
            nomi,
        )
        assertTrue(letti.all { it.codiciIgnorati.isEmpty() })
    }

    @Test
    fun totaleTurniEFestivi() {
        assertEquals(516, letti.sumOf { it.turni.size })
        val giugno = letti.first { it.mese == YearMonth.of(2026, 6) }
        assertEquals(2, giugno.turni.count { it.codice == "GD" })
        assertEquals(2, giugno.turni.count { it.codice == "ND" })
    }

    @Test
    fun cambioOraLegaleOttobre() {
        // 24 ottobre 2026: 24H di SOLA a cavallo del ritorno all'ora solare → 25 ore reali.
        val ottobre = letti.first { it.mese == YearMonth.of(2026, 10) }
        val turno = ottobre.turni.single { it.inizio == millis("2026-10-24T08:00") }
        assertEquals("SOLA GIADA", turno.nome)
        assertEquals(millis("2026-10-25T08:00"), turno.fine)
        assertEquals(Duration.ofHours(25).toMillis(), turno.fine - turno.inizio)
    }

    @Test
    fun annoCompletoSenzaBuchiNeSovrapposizioni() {
        val tutti = letti.flatMap { it.turni }
        val analisi = Validatore.analizza(tutti, millis("2026-01-01T08:00"))
        assertEquals(emptyList<Any>(), analisi.buchi)
        assertEquals(emptyList<Any>(), analisi.sovrapposizioni)
        assertEquals(millis("2026-12-01T08:00"), analisi.fineTurni)
    }
}
