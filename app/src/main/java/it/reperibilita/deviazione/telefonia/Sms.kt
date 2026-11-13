package it.reperibilita.deviazione.telefonia

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Invio di SMS di conferma (dalla SIM di reperibilità). Opzionale: niente permesso = niente SMS, senza errori. */
class Sms(private val context: Context) {

    fun haPermesso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun manager(subId: Int): SmsManager {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        return if (subId >= 0) runCatching { base.createForSubscriptionId(subId) }.getOrDefault(base) else base
    }

    /** Invia [testo] a [numero]. Restituisce true se l'invio è stato consegnato al sistema. */
    @SuppressLint("MissingPermission")
    fun invia(numero: String, testo: String, subId: Int): Boolean {
        val dest = numero.trim()
        if (!haPermesso() || dest.length < 5) return false
        return try {
            val sm = manager(subId)
            val parti = runCatching { sm.divideMessage(testo) }.getOrNull()
            if (parti != null && parti.size > 1) sm.sendMultipartTextMessage(dest, null, parti, null, null)
            else sm.sendTextMessage(dest, null, testo, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
