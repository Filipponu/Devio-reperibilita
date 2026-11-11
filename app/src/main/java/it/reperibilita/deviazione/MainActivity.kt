package it.reperibilita.deviazione

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import it.reperibilita.deviazione.ui.AppNav
import it.reperibilita.deviazione.ui.AppViewModel
import it.reperibilita.deviazione.ui.TemaApp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) apriFileRicevuto(intent)
        setContent { TemaApp { AppNav(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        apriFileRicevuto(intent)
    }

    override fun onResume() {
        super.onResume()
        // Riprogramma gli allarmi e, se un cambio turno è stato saltato, applica subito la deviazione.
        vm.allApertura()
    }

    /** File Excel aperto da Gmail, WhatsApp, File… con "Apri con" o "Condividi". */
    private fun apriFileRicevuto(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        vm.caricaExcel(uri)
    }
}
