package it.reperibilita.deviazione.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.reperibilita.deviazione.MainActivity
import it.reperibilita.deviazione.contenitore
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Livello
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione

/** Widget per la schermata Home: chi è di turno e stato della deviazione. */
class WidgetStato : GlanceAppWidget() {

    companion object {
        suspend fun aggiornaTutti(context: Context) = WidgetStato().updateAll(context)
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val situazione = context.contenitore.motore.situazione()
        provideContent { GlanceTheme { Contenuto(situazione) } }
    }
}

class RicevitoreWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WidgetStato()
}

private fun coloreLivello(l: Livello): Color = when (l) {
    Livello.VERDE -> Color(0xFF1E9E55)
    Livello.ARANCIONE -> Color(0xFFF08C00)
    Livello.ROSSO -> Color(0xFFD32F2F)
    Livello.GRIGIO -> Color(0xFF8A8F98)
}

@Composable
private fun Contenuto(s: Situazione) {
    val colore = ColorProvider(coloreLivello(s.livello))
    val nome = s.collega?.let { Nomi.nomeVisualizzato(it) } ?: s.turno?.nome ?: "Nessuno di turno"
    val dettaglio = buildList {
        s.fineBlocco?.let { add("fino alle ${Formati.ora(it)}") }
        s.numeroAtteso?.let { add(Nomi.formattaTelefono(it)) }
    }.joinToString(" · ")
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(colore)) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    s.titolo,
                    maxLines = 1,
                    style = TextStyle(color = colore, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                nome,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            if (dettaglio.isNotEmpty()) {
                Text(
                    dettaglio,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
    }
}
