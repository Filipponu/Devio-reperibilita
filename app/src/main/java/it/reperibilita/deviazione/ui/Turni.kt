package it.reperibilita.deviazione.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Calendario
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.excel.Validatore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataTurni(
    turni: List<Turno>,
    colleghi: List<Collega>,
    ora: Long,
    onImporta: () -> Unit,
    onCambio: (Turno?) -> Unit,
    onEliminaCambio: (Turno) -> Unit,
) {
    val prossimi = remember(turni, ora / 60_000) { Calendario.prossimi(turni, ora) }
    val analisi = remember(turni, ora / 60_000) { Validatore.analizzaConManuali(turni, ora) }
    var daEliminare by remember { mutableStateOf<Turno?>(null) }
    val corrente = remember(turni, ora / 60_000) { Calendario.corrente(turni, ora) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("Turni")
                        analisi.fineTurni?.let {
                            Text(
                                "Caricati fino a ${Formati.giornoOra(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onCambio(null) }) { Icon(Icons.Rounded.AddCircleOutline, "Aggiungi turno") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImporta,
                icon = { Icon(Icons.Rounded.UploadFile, null) },
                text = { Text("Importa Excel") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            if (analisi.buchi.isNotEmpty() || analisi.sovrapposizioni.isNotEmpty()) {
                item { AvvisiTurni(analisi, colleghi) }
            }
            if (prossimi.isNotEmpty()) {
                item {
                    Text(
                        "Tocca un turno per cambiarlo (anche solo in parte) senza modificare l'Excel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (prossimi.isEmpty()) {
                item { Vuoto("Nessun turno futuro. Importa il file Excel del mese.", Icons.Rounded.UploadFile) }
            }
            itemsIndexed(prossimi, key = { _, t -> t.id }) { i, t ->
                val nuovoGiorno = i == 0 || Formati.zdt(t.inizio).toLocalDate() != Formati.zdt(prossimi[i - 1].inizio).toLocalDate()
                if (nuovoGiorno) IntestazioneGiorno(t.inizio)
                val info = infoRiga(turni, t, colleghi)
                RigaTimeline(
                    t, Nomi.trova(t.nome, colleghi), inCorso = t.id == corrente?.id,
                    primo = nuovoGiorno,
                    ultimo = i == prossimi.lastIndex ||
                        Formati.zdt(prossimi[i + 1].inizio).toLocalDate() != Formati.zdt(t.inizio).toLocalDate(),
                    sostituitoDa = info.sostituitoDa,
                    notaParziale = info.notaParziale,
                    onClick = { if (t.manuale) daEliminare = t else onCambio(t) },
                )
            }
        }
    }

    daEliminare?.let { t ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            icon = { Icon(Icons.Rounded.SwapHoriz, null) },
            title = { Text("Annullare il cambio?") },
            text = {
                Text(
                    "${Nomi.trova(t.nome, colleghi)?.let { Nomi.nomeVisualizzato(it) } ?: t.nome}, " +
                        "${Formati.giornoOra(t.inizio)} → ${Formati.giornoOra(t.fine)}.\n" +
                        "Torna a valere il turno del file Excel.",
                )
            },
            confirmButton = { Button(onClick = { onEliminaCambio(t); daEliminare = null }) { Text("Annulla il cambio") } },
            dismissButton = { TextButton(onClick = { daEliminare = null }) { Text("Lascia") } },
        )
    }
}
