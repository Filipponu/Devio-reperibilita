package it.reperibilita.deviazione.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UploadFile
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
fun SchermataTurni(turni: List<Turno>, colleghi: List<Collega>, ora: Long, onImporta: () -> Unit) {
    val prossimi = remember(turni, ora / 60_000) { Calendario.prossimi(turni, ora) }
    val analisi = remember(turni, ora / 60_000) { Validatore.analizza(turni, ora) }
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
            if (prossimi.isEmpty()) {
                item { Vuoto("Nessun turno futuro. Importa il file Excel del mese.", Icons.Rounded.UploadFile) }
            }
            itemsIndexed(prossimi, key = { _, t -> t.id }) { i, t ->
                val nuovoGiorno = i == 0 || Formati.zdt(t.inizio).toLocalDate() != Formati.zdt(prossimi[i - 1].inizio).toLocalDate()
                if (nuovoGiorno) IntestazioneGiorno(t.inizio)
                RigaTimeline(
                    t, Nomi.trova(t.nome, colleghi), inCorso = t.id == corrente?.id,
                    primo = nuovoGiorno,
                    ultimo = i == prossimi.lastIndex ||
                        Formati.zdt(prossimi[i + 1].inizio).toLocalDate() != Formati.zdt(t.inizio).toLocalDate(),
                )
            }
        }
    }
}
