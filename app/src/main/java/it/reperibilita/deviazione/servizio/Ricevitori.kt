package it.reperibilita.deviazione.servizio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.reperibilita.deviazione.allarme.AllarmeService
import it.reperibilita.deviazione.contenitore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private fun BroadcastReceiver.asincrono(blocco: suspend () -> Unit) {
    val risultato = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            blocco()
        } finally {
            risultato.finish()
        }
    }
}

/** Allarmi programmati dall'app (cambio turno, controllo conferma, allarme sonoro). */
class RicevitoreAllarmi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.contenitore
        when (intent.action) {
            Pianificatore.AZIONE_CAMBIO_TURNO -> DeviazioneService.avvia(context, DeviazioneService.APPLICA)
            Pianificatore.AZIONE_CONTROLLO_CONFERMA -> asincrono {
                c.motore.controlloConferma(intent.getLongExtra(Pianificatore.EXTRA_TENTATIVO, 0L))
            }
            Pianificatore.AZIONE_SUONA_ALLARME -> asincrono {
                if (c.motore.allarmeNecessario()) AllarmeService.avvia(context, c.motore.motivoAllarme())
            }
            Pianificatore.AZIONE_RIPETI_ALLARME -> asincrono {
                if (c.motore.allarmeNecessario()) AllarmeService.avvia(context, c.motore.motivoAllarme())
            }
        }
    }
}

/** Riavvio, aggiornamento dell'app, cambio di ora o di fuso: riprogramma e recupera il turno in corso. */
class RicevitoreSistema : BroadcastReceiver() {
    private val azioni = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in azioni) return
        val c = context.contenitore
        asincrono {
            c.pianificatore.programma()
            c.motore.aggiornaNotificheEWidget()
            if (c.motore.serveRiapplicare()) DeviazioneService.avvia(context, DeviazioneService.APPLICA)
        }
    }
}
