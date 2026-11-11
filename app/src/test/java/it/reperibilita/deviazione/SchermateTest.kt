package it.reperibilita.deviazione

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.reperibilita.deviazione.allarme.SchermataAllarme
import it.reperibilita.deviazione.data.AppDatabase
import it.reperibilita.deviazione.data.Esito
import it.reperibilita.deviazione.data.EventoRegistro
import it.reperibilita.deviazione.data.Preferenze
import it.reperibilita.deviazione.data.Stato
import it.reperibilita.deviazione.data.StatoDeviazione
import it.reperibilita.deviazione.data.TipoEvento
import it.reperibilita.deviazione.dominio.CalcolaSituazione
import it.reperibilita.deviazione.dominio.ZONA_ROMA
import it.reperibilita.deviazione.excel.LettoreTurni
import it.reperibilita.deviazione.excel.PreparaAnteprima
import it.reperibilita.deviazione.excel.XlsxReader
import it.reperibilita.deviazione.ui.SchermataHome
import it.reperibilita.deviazione.ui.SchermataImport
import it.reperibilita.deviazione.ui.SchermataRubrica
import it.reperibilita.deviazione.ui.SchermataTurni
import it.reperibilita.deviazione.ui.DialogCambioTurno
import it.reperibilita.deviazione.ui.StatoImport
import it.reperibilita.deviazione.ui.TemaApp
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** Screenshot delle schermate principali (solo in locale, non in CI). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class SchermateTest {

    @get:Rule
    val compose = createComposeRule()

    private val cartella = "build/schermate"
    private fun ms(s: String) = LocalDateTime.parse(s).atZone(ZONA_ROMA).toInstant().toEpochMilli()

    private val turni by lazy {
        javaClass.classLoader!!.getResourceAsStream("reperibilita-2026.xlsx").use { XlsxReader.leggi(it) }
            .let { LettoreTurni.leggi(it) }.last().turni.mapIndexed { i, t -> t.copy(id = i + 1L) }
    }
    private val colleghi = AppDatabase.RUBRICA_INIZIALE.mapIndexed { i, c -> c.copy(id = i + 1L) }
    private val pref = Preferenze("3929019431", -1, automatico = true, allarmeSenzaConferma = true, prefissoInternazionale = false)

    private fun stato(s: StatoDeviazione, messaggio: String? = null) = Stato(
        numero = "3208783328", nome = "BINJAKU", stato = s, dal = ms("2026-11-12T08:00"), tentativo = ms("2026-11-12T08:00"),
        messaggio = messaggio, verificaQuando = ms("2026-11-12T08:01"),
        verificaTesto = if (s == StatoDeviazione.CONFERMATA) "Inoltro chiamate\nVoce: +393208783328" else null,
        verificaOk = if (s == StatoDeviazione.CONFERMATA) true else null, allarmeAttivo = false, ultimoAvvisoSenzaTurno = 0,
    )

    private val registro = listOf(
        EventoRegistro(3, ms("2026-11-12T08:00"), TipoEvento.ATTIVAZIONE, Esito.CONFERMATA, "3208783328", "BINJAKU", "Confermata dall'operatore"),
        EventoRegistro(2, ms("2026-11-11T20:00"), TipoEvento.ATTIVAZIONE, Esito.CONFERMATA, "3392830941", "NUCERA", "Confermata dall'operatore"),
        EventoRegistro(1, ms("2026-11-11T19:12"), TipoEvento.PROVA_CHIAMATA, Esito.RIUSCITA, "3403718061", "CAVALLARI", "La chiamata di prova è arrivata al collega"),
    )

    @Before
    fun soloInLocale() {
        assumeTrue("Screenshot solo in locale", System.getenv("CI") == null)
    }

    private fun home(nome: String, s: StatoDeviazione, messaggio: String? = null, occupato: String? = null) {
        val ora = ms("2026-11-12T10:00")
        val situazione = CalcolaSituazione.calcola(turni, colleghi, stato(s, messaggio), pref, ora)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaApp {
                SchermataHome(
                    situazione, occupato, turni, colleghi, registro,
                    {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$cartella/$nome.png")
    }

    @Test fun homeConfermata() = home("home_verde", StatoDeviazione.CONFERMATA)
    @Test fun homeInAttesa() = home("home_arancione", StatoDeviazione.IN_ATTESA, "codice inviato, conferma non arrivata", "Verifico con l'operatore (*#21#)…")
    @Test fun homeErrore() = home("home_rossa", StatoDeviazione.ERRORE, "Il telefono/operatore ha risposto: servizio non disponibile")

    @Test
    @Config(qualifiers = "+night")
    fun homeScura() = home("home_scura", StatoDeviazione.CONFERMATA)

    @Test
    fun anteprimaImport() {
        val letti = javaClass.classLoader!!.getResourceAsStream("reperibilita-2026.xlsx").use { XlsxReader.leggi(it) }
            .let { LettoreTurni.leggi(it) }
        val senzaForni = colleghi.filter { it.cognome != "FORNI" }
        val anteprima = PreparaAnteprima.prepara("REPERIBILITà 2026.xlsx", letti, emptyList(), senzaForni, ms("2026-11-11T16:00"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaApp { SchermataImport(StatoImport.Pronta(anteprima), senzaForni, {}, {}, {}, {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$cartella/import.png")
    }

    @Test
    fun allarme() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaApp {
                SchermataAllarme(
                    prova = false,
                    motivo = "L'operatore non ha confermato la deviazione verso BINJAKU entro 2 minuti.",
                    onRiprova = {}, onSpegni = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("$cartella/allarme.png")
    }

    @Test
    fun turniConCambio() {
        val cambio = turni.first { it.nome.startsWith("CAVALLARI") && it.inizio >= ms("2026-11-12T20:00") }
            .let { it.copy(id = 999, nome = "NUCERA FILIPPO", codice = "M", manuale = true) }
        val parziale = turni.first { it.inizio == ms("2026-11-13T08:00") }
            .let { it.copy(id = 998, nome = "FORNI JANA", codice = "M", manuale = true, inizio = ms("2026-11-13T14:00")) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaApp { SchermataTurni(turni + cambio + parziale, colleghi, ms("2026-11-12T10:00"), {}, {}, {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$cartella/turni_cambio.png")
    }

    @Test
    fun dialogCambio() {
        val turno = turni.first { it.inizio == ms("2026-11-12T08:00") }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TemaApp { DialogCambioTurno(turno, colleghi, ms("2026-11-12T10:35"), { _, _, _ -> }, {}) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$cartella/dialog_cambio.png")
    }

    @Test
    fun rubrica() {
        compose.mainClock.autoAdvance = false
        compose.setContent { TemaApp { SchermataRubrica(colleghi, {}, {}) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$cartella/rubrica.png")
    }
}

/** Avvio reale delle activity con database e preferenze veri. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class AvvioTest {

    @Before
    fun permessi() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @Test
    fun avvioApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            scenario.onActivity { a ->
                if (System.getenv("CI") == null) a.window.decorView.captureRoboImage("build/schermate/app_avvio.png")
            }
        }
    }

}
