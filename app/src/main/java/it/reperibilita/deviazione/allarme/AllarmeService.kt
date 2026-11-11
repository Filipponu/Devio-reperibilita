package it.reperibilita.deviazione.allarme

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.reperibilita.deviazione.R
import it.reperibilita.deviazione.contenitore
import it.reperibilita.deviazione.notifiche.Notifiche
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Allarme tipo sveglia: suono in loop sul canale allarme (suona anche in silenzioso / Non disturbare),
 * vibrazione continua e schermata a tutto schermo. Dopo 90 secondi tace e si ripete ogni 5 minuti
 * finché la deviazione non risulta riuscita o l'utente non lo spegne.
 */
class AllarmeService : Service() {

    companion object {
        private const val AVVIA = "avvia"
        private const val FERMA = "ferma"
        private const val SILENZIA = "silenzia"
        private const val EXTRA_MOTIVO = "motivo"
        private const val EXTRA_PROVA = "prova"
        private const val EXTRA_DA_UTENTE = "da_utente"
        private const val DURATA_SUONO_MS = 90_000L

        private val _inSuono = MutableStateFlow(false)
        /** True mentre l'allarme sta suonando (la schermata a tutto schermo si chiude quando torna false). */
        val inSuono: StateFlow<Boolean> = _inSuono.asStateFlow()

        fun avvia(context: Context, motivo: String?, prova: Boolean = false) {
            val i = Intent(context, AllarmeService::class.java).setAction(AVVIA)
                .putExtra(EXTRA_MOTIVO, motivo).putExtra(EXTRA_PROVA, prova)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (e: Exception) {
                Notifiche(context).errore(motivo ?: "Deviazione non riuscita")
            }
        }

        fun intentFerma(context: Context, daUtente: Boolean): Intent =
            Intent(context, AllarmeService::class.java).setAction(FERMA).putExtra(EXTRA_DA_UTENTE, daUtente)

        /** Ferma suono e schermata se l'allarme sta suonando (non tocca le ripetizioni). */
        fun spegniSeAttivo(context: Context) {
            if (_inSuono.value) runCatching { context.startService(intentFerma(context, daUtente = false)) }
        }

        /** Zittisce l'allarme mentre si riprova la deviazione. */
        fun silenzia(context: Context) {
            if (_inSuono.value) runCatching { context.startService(Intent(context, AllarmeService::class.java).setAction(SILENZIA)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var tono: ToneGenerator? = null
    private var tonoJob: Job? = null
    private var vibratore: Vibrator? = null
    private var volumeOriginale: Int? = null
    private var scadenza: Job? = null
    private var suonoJob: Job? = null
    private var verifica: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FERMA -> {
                val daUtente = intent.getBooleanExtra(EXTRA_DA_UTENTE, false)
                termina()
                if (daUtente) {
                    val c = contenitore
                    c.scope.launch { c.motore.fermaAllarme(daUtente = true) }
                }
            }
            SILENZIA -> termina()
            else -> {
                val prova = intent?.getBooleanExtra(EXTRA_PROVA, false) ?: false
                val motivo = intent?.getStringExtra(EXTRA_MOTIVO)
                avviaAllarme(motivo, prova)
            }
        }
        return START_NOT_STICKY
    }

    private fun avviaAllarme(motivo: String?, prova: Boolean) {
        val notifica = Notifiche(this).allarme(motivo, prova)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ServiceCompat.startForeground(this, Notifiche.ID_ALLARME, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } catch (_: Exception) {
                ServiceCompat.startForeground(this, Notifiche.ID_ALLARME, notifica, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            startForeground(Notifiche.ID_ALLARME, notifica)
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "deviazione:allarme")
                .apply { acquire(DURATA_SUONO_MS + 30_000L) }
        }
        if (!_inSuono.value) {
            _inSuono.value = true
            vibra()
            val c = contenitore
            suonoJob = scope.launch {
                val suonoApp = runCatching { c.impostazioni.preferenzeOra().suonoApp }.getOrDefault(true)
                if (_inSuono.value) suona(suonoApp)
            }
        }
        scadenza?.cancel()
        scadenza = scope.launch {
            delay(DURATA_SUONO_MS)
            if (!prova) {
                val c = contenitore
                if (c.motore.allarmeNecessario()) c.pianificatore.programmaRipetizioneAllarme()
            }
            termina()
        }
        if (!prova && verifica?.isActive != true) {
            val c = contenitore
            val app = applicationContext
            verifica = c.scope.launch {
                if (c.motore.verificaSilenziosa()) {
                    Notifiche(app).avviso("Deviazione confermata", "L'operatore ora conferma la deviazione: allarme fermato.")
                    c.motore.fermaAllarme(daUtente = false)
                }
            }
        }
    }

    private val attributiAllarme: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Suono in loop sul canale allarme: per primo quello dell'app (res/raw/allarme.wav) oppure,
     * se scelto nelle Opzioni, la suoneria della sveglia del telefono. L'altro fa da riserva.
     */
    private fun suona(suonoApp: Boolean) {
        val audio = getSystemService(AudioManager::class.java)
        runCatching {
            volumeOriginale = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val dellApp = Uri.parse("android.resource://$packageName/${R.raw.allarme}")
        val delTelefono: List<Uri?> = listOf(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        val suoni = if (suonoApp) listOf(dellApp) + delTelefono else delTelefono + dellApp
        for (uri in suoni.filterNotNull()) {
            val mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributiAllarme)
                    setDataSource(this@AllarmeService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (mp != null) {
                player = mp
                return
            }
        }
        // Nessun suono di sistema disponibile: tono generato sul canale allarme.
        tono = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        tonoJob = scope.launch {
            while (isActive) {
                tono?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1_500)
                delay(2_000)
            }
        }
    }

    private fun vibra() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibratore = v
        val effetto = VibrationEffect.createWaveform(longArrayOf(0, 900, 500, 900, 500), 0)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effetto, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effetto, attributiAllarme)
            }
        }
    }

    private fun zittisci() {
        suonoJob?.cancel()
        suonoJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        tonoJob?.cancel()
        tonoJob = null
        runCatching { tono?.release() }
        tono = null
        runCatching { vibratore?.cancel() }
        vibratore = null
        volumeOriginale?.let { vol ->
            runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) }
        }
        volumeOriginale = null
    }

    private fun termina() {
        scadenza?.cancel()
        zittisci()
        _inSuono.value = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        zittisci()
        _inSuono.value = false
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
