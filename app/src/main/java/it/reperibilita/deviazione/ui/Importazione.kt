package it.reperibilita.deviazione.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.excel.Analisi
import it.reperibilita.deviazione.excel.Anteprima

private fun intervallo(da: Long, a: Long): String {
    val stessoGiorno = Formati.zdt(da).toLocalDate() == Formati.zdt(a).toLocalDate()
    return if (stessoGiorno) "${Formati.giorno(da)}: dalle ${Formati.ora(da)} alle ${Formati.ora(a)}"
    else "dal ${Formati.giornoOra(da)} al ${Formati.giornoOra(a)}"
}

/** Riquadro con buchi e sovrapposizioni. */
@Composable
fun AvvisiTurni(analisi: Analisi, colleghi: List<Collega>) {
    val rosso = LocalTavolozza.current.rosso.inizio
    val arancione = LocalTavolozza.current.arancione.inizio
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (analisi.buchi.isNotEmpty()) {
            Riquadro(
                Icons.Rounded.WarningAmber, rosso,
                if (analisi.buchi.size == 1) "C'è un buco nei turni" else "Ci sono ${analisi.buchi.size} buchi nei turni",
                analisi.buchi.map { "• ${intervallo(it.inizio, it.fine)}: nessuno di turno" } +
                    "In quei periodi resta attiva l'ultima deviazione. Carica il file corretto appena puoi.",
            )
        }
        if (analisi.sovrapposizioni.isNotEmpty()) {
            fun nome(n: String) = Nomi.trova(n, colleghi)?.cognome ?: n
            Riquadro(
                Icons.Rounded.WarningAmber, arancione,
                if (analisi.sovrapposizioni.size == 1) "C'è una sovrapposizione" else "Ci sono ${analisi.sovrapposizioni.size} sovrapposizioni",
                analisi.sovrapposizioni.map {
                    "• ${intervallo(it.inizio, it.fine)}: ${nome(it.primo.nome)} e ${nome(it.secondo.nome)}"
                } + "Durante la sovrapposizione la deviazione va a chi ha iniziato il turno per ultimo.",
            )
        }
    }
}

@Composable
private fun Riquadro(icona: androidx.compose.ui.graphics.vector.ImageVector, colore: Color, titolo: String, righe: List<String>) {
    Surface(shape = MaterialTheme.shapes.medium, color = colore.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Icon(icona, null, tint = colore)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(titolo, style = MaterialTheme.typography.titleSmall, color = colore)
                righe.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataImport(
    stato: StatoImport,
    colleghi: List<Collega>,
    onAnnulla: () -> Unit,
    onSalva: () -> Unit,
    onAltroFile: () -> Unit,
    onAggiungiCollega: (Collega) -> Unit,
) {
    BackHandler(onBack = onAnnulla)
    var daAggiungere by remember { mutableStateOf<String?>(null) }
    var confermaSalva by remember { mutableStateOf(false) }
    val anteprima = (stato as? StatoImport.Pronta)?.anteprima

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anteprima turni") },
                navigationIcon = { IconButton(onClick = onAnnulla) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") } },
            )
        },
        bottomBar = {
            if (anteprima != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onAnnulla, modifier = Modifier.weight(1f).height(56.dp)) {
                            Icon(Icons.Rounded.Close, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Annulla")
                        }
                        Button(
                            onClick = {
                                if (anteprima.analisi.buchi.isNotEmpty() || anteprima.analisi.sovrapposizioni.isNotEmpty()) confermaSalva = true
                                else onSalva()
                            },
                            enabled = !anteprima.niente && anteprima.nomiSconosciuti.isEmpty(),
                            modifier = Modifier.weight(1.4f).height(56.dp),
                        ) {
                            Icon(Icons.Rounded.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Salva ${anteprima.turniDaSalvare.size} turni")
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (stato) {
            StatoImport.Nessuno, StatoImport.Lettura -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Leggo il file Excel…")
                }
            }
            is StatoImport.Errore -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(stato.messaggio, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAltroFile) { Text("Scegli un altro file") }
            }
            is StatoImport.Pronta -> ContenutoAnteprima(stato.anteprima, colleghi, PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp,
            )) { daAggiungere = it }
        }
    }

    daAggiungere?.let { nomeExcel ->
        val (cognome, nome) = Nomi.proponiCognomeNome(nomeExcel)
        DialogCollega(
            iniziale = Collega(cognome = cognome, nome = nome, telefono = ""),
            titolo = "Numero di $nomeExcel",
            onSalva = { onAggiungiCollega(it); daAggiungere = null },
            onElimina = null,
            onChiudi = { daAggiungere = null },
        )
    }
    if (confermaSalva && anteprima != null) {
        AlertDialog(
            onDismissRequest = { confermaSalva = false },
            icon = { Icon(Icons.Rounded.WarningAmber, null) },
            title = { Text("Salvare comunque?") },
            text = {
                Text(
                    "Nei turni ci sono ${anteprima.analisi.buchi.size} buchi e ${anteprima.analisi.sovrapposizioni.size} sovrapposizioni. " +
                        "Puoi salvare ora e caricare il file corretto in seguito: i mesi reimportati vengono sostituiti.",
                )
            },
            confirmButton = { Button(onClick = { confermaSalva = false; onSalva() }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { confermaSalva = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ContenutoAnteprima(a: Anteprima, colleghi: List<Collega>, padding: PaddingValues, onAggiungi: (String) -> Unit) {
    val verde = LocalTavolozza.current.verde.inizio
    val turniFuturi = a.turniDaSalvare.filter { it.fine > System.currentTimeMillis() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Text(a.nomeFile, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(a.mesi, key = { it.foglio.nomeFoglio }) { m ->
            val (icona, colore, testo) = when {
                m.foglio.errore != null -> Triple(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, m.foglio.errore)
                m.passato -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "già concluso, non viene importato")
                m.foglio.turni.isEmpty() -> Triple(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "nessun turno trovato")
                else -> Triple(Icons.Rounded.CheckCircle, verde, "${m.foglio.turni.size} turni da importare")
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icona, null, tint = colore, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(m.foglio.nomeFoglio, style = MaterialTheme.typography.labelLarge)
                Text(" · $testo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (a.niente) {
            item { Vuoto("Nel file non ci sono turni futuri da importare.", Icons.Rounded.Info) }
        }
        if (a.nomiSconosciuti.isNotEmpty()) {
            item { TitoloSezione("Numeri mancanti") }
            items(a.nomiSconosciuti, key = { "n$it" }) { n ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Non è in rubrica", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        FilledTonalButton(onClick = { onAggiungi(n) }) {
                            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Aggiungi")
                        }
                    }
                }
            }
        }
        item { TitoloSezione("Controllo") }
        item {
            if (a.analisi.buchi.isEmpty() && a.analisi.sovrapposizioni.isEmpty() && !a.niente) {
                Riquadro(
                    Icons.Rounded.CheckCircle, verde, "Nessun buco e nessuna sovrapposizione",
                    listOfNotNull(a.analisi.fineTurni?.let { "Reperibilità coperta fino a ${Formati.giornoOra(it)}." }),
                )
            } else {
                AvvisiTurni(a.analisi, colleghi)
            }
            val festivi = a.turniDaSalvare.count { it.codice in setOf("GD", "ND") }
            val ignorati = a.mesi.filter { it.daImportare }.flatMap { it.foglio.codiciIgnorati.entries }
                .groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
            if (festivi > 0 || ignorati.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Riquadro(
                    Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant, "Note sul file",
                    listOfNotNull(
                        if (festivi > 0) "• $festivi turni GD/ND (festivi) trattati come G e N." else null,
                        if (ignorati.isNotEmpty()) "• Valori ignorati: " + ignorati.entries.joinToString { "${it.key} (${it.value})" } else null,
                    ),
                )
            }
        }
        if (turniFuturi.isNotEmpty()) {
            item { TitoloSezione("Turni da salvare") }
            itemsIndexed(turniFuturi, key = { i, t -> "p$i-${t.inizio}-${t.nome}" }) { i, t ->
                val nuovoGiorno = i == 0 || Formati.zdt(t.inizio).toLocalDate() != Formati.zdt(turniFuturi[i - 1].inizio).toLocalDate()
                if (nuovoGiorno) IntestazioneGiorno(t.inizio)
                RigaTimeline(
                    t, Nomi.trova(t.nome, colleghi), inCorso = false, primo = nuovoGiorno,
                    ultimo = i == turniFuturi.lastIndex ||
                        Formati.zdt(turniFuturi[i + 1].inizio).toLocalDate() != Formati.zdt(t.inizio).toLocalDate(),
                )
            }
        }
    }
}
