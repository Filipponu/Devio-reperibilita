package it.reperibilita.deviazione.allarme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.reperibilita.deviazione.contenitore
import it.reperibilita.deviazione.servizio.DeviazioneService
import it.reperibilita.deviazione.ui.TemaApp
import kotlinx.coroutines.launch

/** Schermata a tutto schermo dell'allarme, visibile anche a telefono bloccato. */
class AllarmeActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MOTIVO = "motivo"
        private const val EXTRA_PROVA = "prova"

        fun intent(context: Context, motivo: String?, prova: Boolean): Intent =
            Intent(context, AllarmeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_MOTIVO, motivo)
                .putExtra(EXTRA_PROVA, prova)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val prova = intent.getBooleanExtra(EXTRA_PROVA, false)
        val motivo = intent.getStringExtra(EXTRA_MOTIVO)
        setContent {
            TemaApp {
                val suona by AllarmeService.inSuono.collectAsState()
                var visto by remember { mutableStateOf(false) }
                LaunchedEffect(suona) {
                    if (suona) visto = true
                    if (!suona && visto) finish()
                }
                SchermataAllarme(
                    prova = prova,
                    motivo = motivo,
                    onRiprova = {
                        AllarmeService.silenzia(this)
                        DeviazioneService.avvia(this, DeviazioneService.RIPROVA)
                        finish()
                    },
                    onSpegni = {
                        if (prova) {
                            startService(AllarmeService.intentFerma(this, daUtente = false))
                        } else {
                            val c = contenitore
                            c.scope.launch { c.motore.fermaAllarme(daUtente = true) }
                        }
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
internal fun SchermataAllarme(prova: Boolean, motivo: String?, onRiprova: () -> Unit, onSpegni: () -> Unit) {
    val pulsazione = rememberInfiniteTransition(label = "pulsazione")
    val scala by pulsazione.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scala",
    )
    val rosso = Color(0xFFB3261E)
    Box(
        Modifier
            .fillMaxSize()
            .background(rosso)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(128.dp)
                    .scale(scala)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(
                if (prova) "PROVA ALLARME" else "ATTENZIONE",
                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            )
            Text(
                if (prova) "Così suonerà l'allarme se la deviazione non riesce" else "Deviazione NON riuscita",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            if (!motivo.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(motivo, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(48.dp))
            if (!prova) {
                Button(
                    onClick = onRiprova,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = rosso),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(32.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Riprova deviazione", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
            OutlinedButton(
                onClick = onSpegni,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White)),
            ) {
                Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(32.dp))
                Spacer(Modifier.size(12.dp))
                Text("Ho visto, spegni", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
