package it.reperibilita.deviazione

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.reperibilita.deviazione.allarme.AllarmeService
import it.reperibilita.deviazione.data.AppDatabase
import it.reperibilita.deviazione.data.Esito
import it.reperibilita.deviazione.data.StatoDeviazione
import it.reperibilita.deviazione.data.TipoEvento
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.notifiche.Notifiche
import it.reperibilita.deviazione.servizio.Pianificatore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Flusso completo del Motore con database, preferenze, AlarmManager e notifiche veri (Robolectric).
 * Il telefono simulato non risponde ai codici: l'app usa il metodo di riserva e resta "in attesa di conferma".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MotoreTest {

    private val ora = 3_600_000L

    @After
    fun fine() = Dispatchers.resetMain()

    @Test
    fun flussoCompleto() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<App>()
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
        val c = app.contenitore
        withContext(Dispatchers.IO) { c.db.clearAllTables() }
        AppDatabase.RUBRICA_INIZIALE.forEach { c.db.colleghi().salva(it) }

        val adesso = System.currentTimeMillis()
        c.db.turni().inserisci(
            listOf(
                Turno(inizio = adesso - 2 * ora, fine = adesso + 10 * ora, nome = "BINJAKU ERMIR", codice = "G", mese = "x"),
                Turno(inizio = adesso + 10 * ora, fine = adesso + 22 * ora, nome = "CAVALLARI FRANCESCA", codice = "N", mese = "x"),
                Turno(inizio = adesso + 22 * ora, fine = adesso + 34 * ora, nome = "CAVALLARI  FRANCESCA", codice = "G", mese = "x"),
            ),
        )

        // Situazione: BINJAKU di turno, poi CAVALLARI per 24 ore di fila
        val s = c.motore.situazione()
        assertEquals("BINJAKU", s.collega?.cognome)
        assertEquals(adesso + 10 * ora, s.fineBlocco)
        assertEquals("CAVALLARI", s.prossimoCollega?.cognome)
        assertTrue(c.motore.serveRiapplicare())

        // 1. Applica: il codice parte, la conferma dell'operatore non arriva → in attesa
        c.motore.applica(forzata = false)
        val st = c.impostazioni.statoOra()
        assertEquals("3208783328", st.numero)
        assertEquals("BINJAKU", st.nome)
        assertEquals(StatoDeviazione.IN_ATTESA, st.stato)
        val registro = c.db.registro().osserva(50).first()
        assertEquals(1, registro.count { it.tipo == TipoEvento.ATTIVAZIONE })
        assertEquals(Esito.NON_VERIFICATA, registro.first { it.tipo == TipoEvento.ATTIVAZIONE }.esito)
        assertFalse(c.motore.serveRiapplicare())

        // Allarmi: prossimo cambio turno + controllo della conferma a +2 minuti
        val allarmi = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue(allarmi.any { it.triggerAtMs == adesso + 10 * ora + 500 })
        assertTrue(allarmi.any { it.triggerAtMs == st.tentativo + Pianificatore.ATTESA_CONFERMA_MS })

        // Notifiche: fissa di stato e cambio turno
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        assertNotNull(nm.getNotification(Notifiche.ID_STATO))
        val cambio = nm.getNotification(Notifiche.ID_CAMBIO)
        assertNotNull(cambio)
        assertTrue(cambio.extras.getString("android.title")!!.startsWith("Deviazione attiva → BINJAKU (3208783328) fino alle"))

        // 2. Stesso numero → nessun nuovo codice
        c.motore.applica(forzata = false)
        assertEquals(1, c.db.registro().osserva(50).first().count { it.tipo == TipoEvento.ATTIVAZIONE })

        // 3. Dopo 2 minuti senza conferma → allarme
        c.motore.controlloConferma(st.tentativo)
        val avviato = shadowOf(app).nextStartedService
        assertEquals(AllarmeService::class.java.name, avviato.component?.className)
        assertTrue(c.impostazioni.statoOra().allarmeAttivo)
        assertNotNull(nm.getNotification(Notifiche.ID_ERRORE))
        c.motore.fermaAllarme(daUtente = true)
        assertFalse(c.impostazioni.statoOra().allarmeAttivo)

        // 4. La prova di chiamata riuscita conferma la deviazione
        c.motore.esitoProvaChiamata(true)
        assertEquals(StatoDeviazione.CONFERMATA, c.impostazioni.statoOra().stato)

        // 5. Disattiva: automatismo sospeso, nessun numero attivo
        c.motore.disattiva()
        val dopo = c.impostazioni.statoOra()
        assertEquals(StatoDeviazione.DISATTIVATA, dopo.stato)
        assertNull(dopo.numero)
        assertFalse(c.impostazioni.preferenzeOra().automatico)
        assertFalse(c.motore.serveRiapplicare())
        assertTrue(c.db.registro().osserva(50).first().any { it.tipo == TipoEvento.DISATTIVAZIONE })
    }
}
