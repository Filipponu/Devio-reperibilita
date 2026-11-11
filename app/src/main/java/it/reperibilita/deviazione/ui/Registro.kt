package it.reperibilita.deviazione.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Esito
import it.reperibilita.deviazione.data.EventoRegistro
import it.reperibilita.deviazione.data.TipoEvento
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi

private fun titoloEvento(e: EventoRegistro): String = when (e.tipo) {
    TipoEvento.ATTIVAZIONE -> when (e.esito) {
        Esito.CONFERMATA -> "Deviazione attivata e confermata"
        Esito.NON_VERIFICATA -> "Deviazione inviata, senza conferma"
        Esito.ERRORE -> "Deviazione NON riuscita"
        else -> "Deviazione"
    }
    TipoEvento.DISATTIVAZIONE -> if (e.esito == Esito.ERRORE) "Disattivazione non riuscita" else "Deviazione disattivata"
    TipoEvento.VERIFICA -> "Verifica stato (*#21#)"
    TipoEvento.PROVA_CHIAMATA -> if (e.esito == Esito.RIUSCITA) "Prova di chiamata riuscita" else "Prova di chiamata NON riuscita"
    TipoEvento.IMPORTAZIONE -> "Turni importati"
    TipoEvento.CAMBIO_TURNO -> "Cambio turno manuale"
    TipoEvento.ALLARME -> "Allarme"
    TipoEvento.INFO -> "Informazione"
}

@Composable
fun RigaRegistro(e: EventoRegistro) {
    val t = LocalTavolozza.current
    val (icona, colore) = when (e.esito) {
        Esito.CONFERMATA, Esito.RIUSCITA -> Icons.Rounded.CheckCircle to t.verde.inizio
        Esito.NON_VERIFICATA -> Icons.Rounded.HourglassTop to t.arancione.inizio
        Esito.ERRORE, Esito.NON_RIUSCITA -> Icons.Rounded.Error to t.rosso.inizio
        Esito.INFO -> (if (e.tipo == TipoEvento.PROVA_CHIAMATA) Icons.Rounded.PhoneCallback else Icons.Rounded.Info) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icona, null, tint = colore, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titoloEvento(e), style = MaterialTheme.typography.titleSmall)
                val chi = listOfNotNull(e.nome, e.numero?.let { Nomi.formattaTelefono(it) }).joinToString(" · ")
                if (chi.isNotEmpty()) Text(chi, style = MaterialTheme.typography.bodyMedium)
                e.dettaglio?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Formati.ora(e.quando), style = MaterialTheme.typography.labelLarge)
                Text(
                    Formati.zdt(e.quando).let { "${it.dayOfMonth}/${it.monthValue}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataRegistro(registro: List<EventoRegistro>) {
    Scaffold(topBar = { TopAppBar(title = { Text("Registro") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            if (registro.isEmpty()) {
                item { Vuoto("Qui compariranno tutte le deviazioni con data, ora, numero ed esito.", Icons.Rounded.Info) }
            }
            itemsIndexed(registro, key = { _, e -> e.id }) { i, e ->
                if (i == 0 || Formati.zdt(e.quando).toLocalDate() != Formati.zdt(registro[i - 1].quando).toLocalDate()) {
                    IntestazioneGiorno(e.quando)
                }
                RigaRegistro(e)
            }
        }
    }
}
