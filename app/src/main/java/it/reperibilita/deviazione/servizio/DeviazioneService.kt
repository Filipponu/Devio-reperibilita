package it.reperibilita.deviazione.servizio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.reperibilita.deviazione.contenitore
import it.reperibilita.deviazione.notifiche.Notifiche
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Servizio in primo piano breve: vive solo il tempo di parlare con l'operatore
 * (invio del codice, verifica, eventuale secondo tentativo) e poi si chiude.
 */
class DeviazioneService : Service() {

    companion object {
        /** Cambio turno o recupero: applica solo se serve (stesso numero → nessun nuovo codice). */
        const val APPLICA = "applica"
        /** "Applica ora": invia il codice anche se il numero non è cambiato. */
        const val APPLICA_ORA = "applica_ora"
        const val RIPROVA = "riprova"
        const val DISATTIVA = "disattiva"
        const val VERIFICA = "verifica"

        fun intent(context: Context, azione: String): Intent =
            Intent(context, DeviazioneService::class.java).setAction(azione)

        /** Avvia il servizio; se Android lo impedisce, avvisa con una notifica di errore. */
        fun avvia(context: Context, azione: String): Boolean = try {
            ContextCompat.startForegroundService(context, intent(context, azione))
            true
        } catch (e: Exception) {
            Notifiche(context).errore(
                "Android non ha permesso di avviare la deviazione in background (${e.javaClass.simpleName}). " +
                    "Tocca Riprova o apri l'app, e controlla che l'app sia esclusa dall'ottimizzazione batteria.",
            )
            false
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val unaAllaVolta = Mutex()
    private var lavoriInCorso = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        inPrimoPiano()
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "deviazione:servizio")
                .apply { acquire(4 * 60_000L) }
        }
        val azione = intent?.action ?: APPLICA
        lavoriInCorso++
        scope.launch {
            try {
                unaAllaVolta.withLock { esegui(azione) }
            } catch (e: Exception) {
                Notifiche(this@DeviazioneService).errore("Errore imprevisto: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                lavoriInCorso--
                if (lavoriInCorso == 0) chiudi()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun esegui(azione: String) {
        val motore = contenitore.motore
        when (azione) {
            APPLICA -> motore.applica(forzata = false)
            APPLICA_ORA, RIPROVA -> motore.applica(forzata = true)
            DISATTIVA -> motore.disattiva()
            VERIFICA -> motore.verifica()
        }
    }

    private fun inPrimoPiano() {
        val notifica = Notifiche(this).lavoro("Comunico con l'operatore…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ServiceCompat.startForeground(this, Notifiche.ID_LAVORO, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } catch (_: Exception) {
                ServiceCompat.startForeground(this, Notifiche.ID_LAVORO, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            }
        } else {
            startForeground(Notifiche.ID_LAVORO, notifica)
        }
    }

    private fun chiudi() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Solo se è stato usato il tipo "shortService" (limite di circa 3 minuti). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        chiudi()
    }

    @Deprecated("Android 14")
    override fun onTimeout(startId: Int) {
        chiudi()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
