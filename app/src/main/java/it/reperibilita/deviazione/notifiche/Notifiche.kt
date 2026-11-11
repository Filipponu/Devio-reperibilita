package it.reperibilita.deviazione.notifiche

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.reperibilita.deviazione.MainActivity
import it.reperibilita.deviazione.R
import it.reperibilita.deviazione.allarme.AllarmeActivity
import it.reperibilita.deviazione.allarme.AllarmeService
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Livello
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione
import it.reperibilita.deviazione.servizio.DeviazioneService

class Notifiche(private val context: Context) {

    companion object {
        const val CANALE_STATO = "stato"
        const val CANALE_CAMBI = "cambi"
        const val CANALE_ERRORI = "errori"
        const val CANALE_ALLARME = "allarme"
        const val CANALE_LAVORO = "lavoro"
        const val CANALE_AVVISI = "avvisi"

        const val ID_STATO = 1
        const val ID_CAMBIO = 2
        const val ID_ERRORE = 3
        const val ID_ALLARME = 4
        const val ID_LAVORO = 5
        const val ID_AVVISO = 6
    }

    private val manager = NotificationManagerCompat.from(context)

    fun creaCanali() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val stato = NotificationChannel(CANALE_STATO, "Stato attuale", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notifica fissa e silenziosa con chi è di turno"
            setShowBadge(false)
        }
        val cambi = NotificationChannel(CANALE_CAMBI, "Cambi turno", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avviso a ogni cambio della deviazione"
        }
        val errori = NotificationChannel(CANALE_ERRORI, "Errori di deviazione", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Deviazione non riuscita"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        val allarme = NotificationChannel(CANALE_ALLARME, "Allarme", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Allarme a tutto schermo (il suono lo gestisce l'app)"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val lavoro = NotificationChannel(CANALE_LAVORO, "Operazioni in corso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mentre l'app parla con l'operatore"
            setShowBadge(false)
        }
        val avvisi = NotificationChannel(CANALE_AVVISI, "Avvisi", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Turni mancanti, file da importare"
        }
        nm.createNotificationChannels(listOf(stato, cambi, errori, allarme, lavoro, avvisi))
    }

    private fun puoNotificare(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun mostra(id: Int, n: Notification) {
        if (puoNotificare()) runCatching { manager.notify(id, n) }
    }

    private fun apriApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun riprova(): PendingIntent = PendingIntent.getForegroundService(
        context, 10, DeviazioneService.intent(context, DeviazioneService.RIPROVA),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(canale: String) = NotificationCompat.Builder(context, canale)
        .setSmallIcon(R.drawable.ic_notifica)
        .setContentIntent(apriApp())
        .setColor(ContextCompat.getColor(context, R.color.primario))

    /** Notifica fissa e silenziosa: chi è di turno e fino a quando. */
    fun stato(s: Situazione) {
        val numero = s.stato.numero?.let { " → ${Nomi.formattaTelefono(it)}" }.orEmpty()
        val testo = when (s.livello) {
            Livello.VERDE -> "✓ ${s.titolo}$numero"
            Livello.ARANCIONE -> "⏳ ${s.titolo}$numero"
            Livello.ROSSO -> "⚠ ${s.titolo}${s.stato.messaggio?.let { " – $it" }.orEmpty()}"
            Livello.GRIGIO -> s.titolo + numero
        }
        val titolo = if (s.turno != null) "Di turno: ${s.riassunto}" else "Nessuno di turno"
        mostra(
            ID_STATO,
            base(CANALE_STATO)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    /** "Deviazione attiva → COGNOME (numero) fino alle HH:MM" con l'esito della conferma dell'operatore. */
    fun cambio(collega: Collega, fine: Long?, confermata: Boolean, dettaglio: String?) {
        val fino = fine?.let { " fino alle ${Formati.ora(it)}" }.orEmpty()
        val titolo = "Deviazione attiva → ${collega.cognome.uppercase()} (${collega.telefono})$fino"
        val testo = if (confermata) "✓ Conferma dell'operatore arrivata"
        else "⚠ Conferma dell'operatore non arrivata" + dettaglio?.let { ": $it" }.orEmpty()
        mostra(
            ID_CAMBIO,
            base(CANALE_CAMBI)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    /** Errore ad alta priorità con suono e pulsante "Riprova". */
    fun errore(motivo: String, conRiprova: Boolean = true) {
        val b = base(CANALE_ERRORI)
            .setContentTitle("ATTENZIONE: deviazione NON riuscita")
            .setContentText(motivo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(motivo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(ContextCompat.getColor(context, R.color.rosso))
            .setAutoCancel(false)
        if (conRiprova) b.addAction(R.drawable.ic_notifica, "Riprova", riprova())
        mostra(ID_ERRORE, b.build())
    }

    fun annullaErrore() = manager.cancel(ID_ERRORE)

    fun avviso(titolo: String, testo: String) {
        mostra(
            ID_AVVISO,
            base(CANALE_AVVISI)
                .setContentTitle(titolo)
                .setContentText(testo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Notifica del servizio in primo piano mentre si parla con l'operatore. */
    fun lavoro(testo: String): Notification = base(CANALE_LAVORO)
        .setContentTitle("Deviazione Reperibilità")
        .setContentText(testo)
        .setOngoing(true)
        .setSilent(true)
        .setProgress(0, 0, true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    /** Notifica dell'allarme con schermata a tutto schermo. */
    fun allarme(motivo: String?, prova: Boolean): Notification {
        val schermo = PendingIntent.getActivity(
            context, 20, AllarmeActivity.intent(context, motivo, prova),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val spegni = PendingIntent.getService(
            context, 21, AllarmeService.intentFerma(context, daUtente = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val titolo = if (prova) "PROVA ALLARME" else "ATTENZIONE: deviazione NON riuscita"
        val b = NotificationCompat.Builder(context, CANALE_ALLARME)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(ContextCompat.getColor(context, R.color.rosso))
            .setContentTitle(titolo)
            .setContentText(motivo ?: "Controlla la deviazione delle chiamate")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(schermo)
            .setFullScreenIntent(schermo, true)
        if (!prova) b.addAction(R.drawable.ic_notifica, "Riprova deviazione", riprova())
        b.addAction(R.drawable.ic_notifica, "Ho visto, spegni", spegni)
        return b.build()
    }
}
