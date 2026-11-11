package it.reperibilita.deviazione

import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Calendario
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.TipoTurno
import it.reperibilita.deviazione.dominio.ZONA_ROMA
import it.reperibilita.deviazione.excel.Foglio
import it.reperibilita.deviazione.excel.LettoreTurni
import it.reperibilita.deviazione.excel.Validatore
import it.reperibilita.deviazione.telefonia.InterpreteRisposta
import it.reperibilita.deviazione.telefonia.StatoOperatore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class LogicaTest {

    private fun ms(s: String) = LocalDateTime.parse(s).atZone(ZONA_ROMA).toInstant().toEpochMilli()
    private fun turno(id: Long, nome: String, da: String, a: String, codice: String = "G") =
        Turno(id = id, inizio = ms(da), fine = ms(a), nome = nome, codice = codice, mese = da.take(7))

    private val rubrica = listOf(
        Collega(1, "CAVALLARI", "Francesca", "3403718061"),
        Collega(2, "SOLA", "Giada", "3423978822"),
        Collega(3, "BINJAKU", "Ermir", "3208783328"),
        Collega(4, "FORNI", "Jana", "3402505457"),
        Collega(5, "NUCERA", "Filippo", "3392830941"),
    )

    @Test
    fun abbinaNomiExcelAllaRubrica() {
        assertEquals(1L, Nomi.trova("CAVALLARI  FRANCESCA", rubrica)?.id)
        assertEquals(5L, Nomi.trova("nucera filippo", rubrica)?.id)
        assertEquals(2L, Nomi.trova("SOLA GIADA", rubrica)?.id)
        assertNull(Nomi.trova("ESPOSITO CHIARA", rubrica))
        assertNull(Nomi.trova("SOLARI MARCO", rubrica))
        // Due colleghi con lo stesso cognome: decide il nome
        val doppi = rubrica + Collega(6, "SOLA", "Marco", "3330000000")
        assertEquals(6L, Nomi.trova("SOLA MARCO", doppi)?.id)
        assertEquals(2L, Nomi.trova("SOLA GIADA", doppi)?.id)
    }

    @Test
    fun numeriDiTelefono() {
        assertTrue(Nomi.stessoNumero("+39 339 283 0941", "3392830941"))
        assertTrue(Nomi.stessoNumero("00393392830941", "339-283-0941"))
        assertFalse(Nomi.stessoNumero("3392830942", "3392830941"))
        assertFalse(Nomi.stessoNumero(null, "3392830941"))
        assertEquals("339 283 0941", Nomi.formattaTelefono("+393392830941"))
        assertEquals("+393392830941", Nomi.pulisciTelefono("+39 339 283-0941"))
        assertEquals("ESPOSITO" to "Chiara", Nomi.proponiCognomeNome("ESPOSITO CHIARA"))
        assertEquals("FC", Nomi.iniziali(rubrica[0]))
    }

    @Test
    fun codiciDeiTurni() {
        assertEquals(TipoTurno.GIORNO, TipoTurno.daCodice("g"))
        assertEquals(TipoTurno.GIORNO, TipoTurno.daCodice("GD"))
        assertEquals(TipoTurno.NOTTE, TipoTurno.daCodice("ND"))
        assertEquals(TipoTurno.H24, TipoTurno.daCodice("24 H"))
        assertEquals(TipoTurno.H24, TipoTurno.daCodice("24HD"))
        assertNull(TipoTurno.daCodice("CORSO"))
        assertNull(TipoTurno.daCodice("LIBERO"))
        assertNull(TipoTurno.daCodice(""))
    }

    @Test
    fun meseDalTesto() {
        assertEquals(YearMonth.of(2026, 11), LettoreTurni.meseDaTesto("NOVEMBRE 2026"))
        assertEquals(YearMonth.of(2027, 1), LettoreTurni.meseDaTesto("Reperibilità gennaio 2027"))
        assertEquals(YearMonth.of(2026, 12), LettoreTurni.meseDaTesto("DIC 26"))
        assertNull(LettoreTurni.meseDaTesto("Foglio1"))
    }

    @Test
    fun foglioSinteticoConCodiciIgnoratiERigaGiorni() {
        val giorni = (1..31).associate { (it + 1) to it.toString() }
        val settimana = (1..31).associate { (it + 1) to listOf("L", "M", "M", "G", "V", "S", "D")[(it - 1) % 7] }
        val celle = mapOf(
            1 to mapOf(1 to "TURNI DICEMBRE 2026"),
            2 to mapOf(1 to "INFERMIERE") + giorni,
            3 to mapOf(1 to "GIORNO") + settimana,
            4 to mapOf(1 to "ROSSI MARIO", 2 to "G", 3 to "corso", 4 to "24 h", 5 to " "),
            5 to mapOf(1 to "Dalle 08 alle 20", 2 to "G"),
            6 to mapOf(1 to "LEGENDA :"),
            7 to mapOf(1 to "BIANCHI LUCA", 2 to "N"),
        )
        val r = LettoreTurni.leggiFoglio(Foglio("Foglio1", celle))
        assertNull(r.errore)
        assertEquals(YearMonth.of(2026, 12), r.mese)
        assertEquals(listOf("G", "24H"), r.turni.map { it.codice })
        assertTrue(r.turni.all { it.nome == "ROSSI MARIO" })
        assertEquals(mapOf("CORSO" to 1), r.codiciIgnorati)
    }

    @Test
    fun foglioSenzaGiorni() {
        val r = LettoreTurni.leggiFoglio(Foglio("NOVEMBRE 2026", mapOf(1 to mapOf(1 to "ciao"))))
        assertTrue(r.errore != null)
    }

    @Test
    fun buchiESovrapposizioni() {
        val turni = listOf(
            turno(1, "A", "2026-12-01T08:00", "2026-12-01T20:00"),
            turno(2, "B", "2026-12-01T20:00", "2026-12-02T08:00", "N"),
            // buco 2/12 08:00 → 20:00
            turno(3, "C", "2026-12-02T20:00", "2026-12-03T08:00", "N"),
            turno(4, "D", "2026-12-03T08:00", "2026-12-04T08:00", "24H"),
            turno(5, "E", "2026-12-03T20:00", "2026-12-04T08:00", "N"), // sovrapposto a D
        )
        val a = Validatore.analizza(turni, ms("2026-12-01T08:00"))
        assertEquals(1, a.buchi.size)
        assertEquals(ms("2026-12-02T08:00"), a.buchi[0].inizio)
        assertEquals(ms("2026-12-02T20:00"), a.buchi[0].fine)
        assertEquals(1, a.sovrapposizioni.size)
        assertEquals("D", a.sovrapposizioni[0].primo.nome)
        assertEquals("E", a.sovrapposizioni[0].secondo.nome)
        assertEquals(ms("2026-12-04T08:00"), a.fineTurni)

        // Se adesso non è coperto, segnala il buco fino al primo turno
        val b = Validatore.analizza(turni, ms("2026-11-30T12:00"))
        assertEquals(ms("2026-11-30T12:00"), b.buchi.first().inizio)
        assertEquals(ms("2026-12-01T08:00"), b.buchi.first().fine)
    }

    @Test
    fun calendario() {
        val turni = listOf(
            turno(1, "SOLA GIADA", "2026-11-12T08:00", "2026-11-12T20:00"),
            turno(2, "SOLA GIADA", "2026-11-12T20:00", "2026-11-13T08:00", "N"),
            turno(3, "NUCERA FILIPPO", "2026-11-13T08:00", "2026-11-13T20:00"),
        )
        val ora = ms("2026-11-12T10:00")
        assertEquals(1L, Calendario.corrente(turni, ora)?.id)
        assertEquals(ms("2026-11-12T20:00"), Calendario.prossimoConfine(turni, ora))
        val numero = { t: Turno -> Nomi.trova(t.nome, rubrica)?.telefono }
        assertEquals(ms("2026-11-13T08:00"), Calendario.fineBlocco(turni, turni[0], numero))
        assertEquals(3L, Calendario.successivo(turni, ms("2026-11-13T08:00"))?.id)
        assertNull(Calendario.corrente(turni, ms("2026-11-13T21:00")))
        assertNull(Calendario.prossimoConfine(turni, ms("2026-11-13T21:00")))
    }

    @Test
    fun risposteOperatore() {
        val attiva = InterpreteRisposta.stato("Inoltro chiamate\nVoce: +393392830941")
        assertTrue(attiva is StatoOperatore.Attiva)
        assertTrue(Nomi.stessoNumero((attiva as StatoOperatore.Attiva).numeri.first(), "3392830941"))
        val mista = InterpreteRisposta.stato("Voce: 340 371 8061\nDati: Non attivo\nFax: Non inoltrato")
        assertEquals(StatoOperatore.Attiva(listOf("3403718061")), mista)
        assertEquals(StatoOperatore.NonAttiva, InterpreteRisposta.stato("Servizio non attivo"))
        assertEquals(StatoOperatore.NonAttiva, InterpreteRisposta.stato("Il servizio è stato disattivato."))
        assertEquals(StatoOperatore.NonAttiva, InterpreteRisposta.stato("Call forwarding\nVoice: Not forwarded"))
        assertTrue(InterpreteRisposta.stato("Richiesta in corso") is StatoOperatore.Sconosciuto)

        assertEquals(true, InterpreteRisposta.comandoRiuscito("Servizio attivato"))
        assertEquals(true, InterpreteRisposta.comandoRiuscito("Registrazione riuscita."))
        assertEquals(true, InterpreteRisposta.comandoRiuscito("Servizio disattivato"))
        assertEquals(false, InterpreteRisposta.comandoRiuscito("Problema di connessione o codice MMI non valido."))
        assertEquals(false, InterpreteRisposta.comandoRiuscito("Servizio non attivato"))
        assertNull(InterpreteRisposta.comandoRiuscito("Codice inviato"))
    }
}
