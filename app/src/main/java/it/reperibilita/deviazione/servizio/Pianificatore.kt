package it.reperibilita.deviazione.servizio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import it.reperibilita.deviazione.MainActivity
import it.reperibilita.deviazione.data.AppDatabase
import it.reperibilita.deviazione.dominio.Calendario

/**
 * Programmazione con AlarmManager. Un solo allarme esatto è sempre puntato al prossimo inizio/fine turno;
 * quando scatta, il servizio applica la deviazione e riprogramma il successivo.
 */
class Pianificatore(private val context: Context, private val db: AppDatabase) {

    companion object {
        const val AZIONE_CAMBIO_TURNO = "it.reperibilita.deviazione.CAMBIO_TURNO"
        const val AZIONE_CONTROLLO_CONFERMA = "it.reperibilita.deviazione.CONTROLLO_CONFERMA"
        const val AZIONE_SUONA_ALLARME = "it.reperibilita.deviazione.SUONA_ALLARME"
        const val AZIONE_RIPETI_ALLARME = "it.reperibilita.deviazione.RIPETI_ALLARME"
        const val EXTRA_TENTATIVO = "tentativo"

        private const val RC_CAMBIO = 100
        private const val RC_CONTROLLO = 101
        private const val RC_SUONA = 102
        private const val RC_RIPETI = 103

        const val ATTESA_CONFERMA_MS = 2 * 60_000L
        const val RIPETIZIONE_ALLARME_MS = 5 * 60_000L
    }

    private val am = context.getSystemService(AlarmManager::class.java)

    fun puoProgrammareEsatti(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pending(azione: String, rc: Int, tentativo: Long? = null): PendingIntent {
        val intent = Intent(context, RicevitoreAllarmi::class.java).setAction(azione)
        if (tentativo != null) intent.putExtra(EXTRA_TENTATIVO, tentativo)
        return PendingIntent.getBroadcast(
            context, rc, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun esatto(quando: Long, pi: PendingIntent) {
        try {
            if (puoProgrammareEsatti()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }

    /** Punta l'allarme al prossimo cambio turno. Restituisce l'orario programmato (o null se non ci sono turni). */
    suspend fun programma(): Long? {
        val prossimo = Calendario.prossimoConfine(db.turni().tutti(), System.currentTimeMillis())
        val pi = pending(AZIONE_CAMBIO_TURNO, RC_CAMBIO)
        if (prossimo == null) {
            am.cancel(pi)
            return null
        }
        esatto(prossimo + 500, pi)
        return prossimo
    }

    /** Controllo a +2 minuti: se l'operatore non ha confermato, parte l'allarme. */
    fun programmaControlloConferma(tentativo: Long) =
        esatto(tentativo + ATTESA_CONFERMA_MS, pending(AZIONE_CONTROLLO_CONFERMA, RC_CONTROLLO, tentativo))

    fun annullaControlloConferma() = am.cancel(pending(AZIONE_CONTROLLO_CONFERMA, RC_CONTROLLO))

    /** Fa partire l'allarme passando da AlarmManager (così il servizio può avviarsi anche in background). */
    fun suonaAllarmeSubito() = esatto(System.currentTimeMillis() + 1_000, pending(AZIONE_SUONA_ALLARME, RC_SUONA))

    /** Ripetizione dell'allarme come una sveglia (setAlarmClock non è limitato dal risparmio energetico). */
    fun programmaRipetizioneAllarme(quando: Long = System.currentTimeMillis() + RIPETIZIONE_ALLARME_MS) {
        val mostra = PendingIntent.getActivity(
            context, 104, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val pi = pending(AZIONE_RIPETI_ALLARME, RC_RIPETI)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(quando, mostra), pi)
        } catch (_: SecurityException) {
            esatto(quando, pi)
        }
    }

    fun annullaRipetizioneAllarme() {
        am.cancel(pending(AZIONE_RIPETI_ALLARME, RC_RIPETI))
        am.cancel(pending(AZIONE_SUONA_ALLARME, RC_SUONA))
    }
}
