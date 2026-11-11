package it.reperibilita.deviazione.telefonia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import it.reperibilita.deviazione.dominio.Nomi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Risultato dell'invio di un codice MMI. */
sealed interface RisultatoMmi {
    /** L'operatore ha risposto (testo leggibile dall'app). */
    data class Risposta(val testo: String) : RisultatoMmi

    /** L'operatore o il telefono hanno rifiutato la richiesta. */
    data class Rifiutato(val motivo: String) : RisultatoMmi

    /** Codice inviato con il metodo di riserva: la risposta appare in un popup di sistema e l'app non può leggerla. */
    data class InviatoSenzaRisposta(val metodo: String) : RisultatoMmi

    /** Non è stato possibile inviare il codice. */
    data class Errore(val motivo: String) : RisultatoMmi
}

data class Sim(val subId: Int, val etichetta: String)

/**
 * Invio dei codici MMI di deviazione:
 * 1. TelephonyManager.sendUssdRequest (permesso CALL_PHONE), leggendo la risposta nel callback;
 * 2. se fallisce: Intent ACTION_CALL con il codice codificato (# → %23) quando l'app è aperta,
 *    altrimenti TelecomManager.placeCall (stessa cosa, ma permessa anche in background).
 */
class Telefonia(private val context: Context) {

    /** Un codice MMI alla volta: richieste parallele verrebbero rifiutate dal telefono. */
    private val unoAllaVolta = Mutex()

    companion object {
        const val VERIFICA = "*#21#"
        const val DISATTIVA = "##21#"
        private const val ATTESA_RISPOSTA_MS = 45_000L

        fun codiceAttivazione(numero: String, internazionale: Boolean): String {
            var n = Nomi.pulisciTelefono(numero)
            if (internazionale && !n.startsWith("+") && !n.startsWith("00")) n = "+39" + Nomi.nazionale(n)
            return "**21*$n#"
        }
    }

    fun haPermessoChiamate(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun haPermessoStatoTelefono(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** SIM attive (serve READ_PHONE_STATE). */
    @SuppressLint("MissingPermission")
    fun simDisponibili(): List<Sim> {
        if (!haPermessoStatoTelefono()) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map { info ->
                val nome = listOfNotNull(
                    info.displayName?.toString()?.takeIf { it.isNotBlank() },
                    info.carrierName?.toString()?.takeIf { it.isNotBlank() && it != info.displayName?.toString() },
                ).joinToString(" · ")
                Sim(info.subscriptionId, "SIM ${info.simSlotIndex + 1}" + if (nome.isNotEmpty()) " – $nome" else "")
            }
        }.getOrDefault(emptyList())
    }

    private fun telephonyManager(subId: Int): TelephonyManager {
        val base = context.getSystemService(TelephonyManager::class.java)
        return if (subId >= 0) base.createForSubscriptionId(subId) else base
    }

    /**
     * Invia un codice MMI. Con [riserva] = true, se sendUssdRequest non funziona usa il metodo di riserva
     * (la risposta in quel caso la mostra Android in un popup).
     */
    suspend fun invia(codice: String, subId: Int, riserva: Boolean): RisultatoMmi = unoAllaVolta.withLock {
        if (!haPermessoChiamate()) return@withLock RisultatoMmi.Errore("Manca il permesso Telefono (chiamate)")
        val ussd = inviaUssd(codice, subId)
        if (ussd is RisultatoMmi.Risposta || !riserva) return@withLock ussd
        inviaDiRiserva(codice, subId) ?: ussd
    }

    @SuppressLint("MissingPermission")
    private suspend fun inviaUssd(codice: String, subId: Int): RisultatoMmi = withContext(Dispatchers.Main) {
        val tm = telephonyManager(subId)
        withTimeoutOrNull(ATTESA_RISPOSTA_MS) {
            suspendCancellableCoroutine<RisultatoMmi> { cont ->
                try {
                    tm.sendUssdRequest(codice, object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephonyManager: TelephonyManager, request: String, response: CharSequence,
                        ) {
                            if (cont.isActive) cont.resume(RisultatoMmi.Risposta(response.toString()))
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephonyManager: TelephonyManager, request: String, failureCode: Int,
                        ) {
                            val motivo = when (failureCode) {
                                TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "servizio non disponibile"
                                TelephonyManager.USSD_RETURN_FAILURE -> "richiesta rifiutata"
                                else -> "codice di errore $failureCode"
                            }
                            if (cont.isActive) cont.resume(RisultatoMmi.Rifiutato("Il telefono/operatore ha risposto: $motivo"))
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(RisultatoMmi.Errore("Permesso negato: ${e.message}"))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(RisultatoMmi.Errore("Invio non riuscito: ${e.message ?: e.javaClass.simpleName}"))
                }
            }
        } ?: RisultatoMmi.Rifiutato("Nessuna risposta dall'operatore entro ${ATTESA_RISPOSTA_MS / 1000} secondi")
    }

    private fun appInPrimoPiano(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    @SuppressLint("MissingPermission")
    private fun accountPerSim(subId: Int): PhoneAccountHandle? {
        if (subId < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !haPermessoStatoTelefono()) return null
        return runCatching {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val tm = context.getSystemService(TelephonyManager::class.java)
            telecom.callCapablePhoneAccounts.firstOrNull { tm.getSubscriptionId(it) == subId }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun inviaDiRiserva(codice: String, subId: Int): RisultatoMmi? = withContext(Dispatchers.Main) {
        val account = accountPerSim(subId)
        val uri = Uri.parse("tel:" + codice.replace("#", "%23"))
        if (appInPrimoPiano()) {
            try {
                val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (account != null) intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                context.startActivity(intent)
                return@withContext RisultatoMmi.InviatoSenzaRisposta("chiamata al codice (ACTION_CALL)")
            } catch (_: Exception) {
                // si prova con placeCall
            }
        }
        try {
            val extra = Bundle()
            if (account != null) extra.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            context.getSystemService(TelecomManager::class.java).placeCall(uri, extra)
            RisultatoMmi.InviatoSenzaRisposta("chiamata al codice (Telecom)")
        } catch (e: Exception) {
            null
        }
    }
}
