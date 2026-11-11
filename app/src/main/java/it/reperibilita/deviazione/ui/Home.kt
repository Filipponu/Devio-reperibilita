package it.reperibilita.deviazione.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.EventoRegistro
import it.reperibilita.deviazione.data.StatoDeviazione
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Calendario
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Livello
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataHome(
    situazione: Situazione?,
    occupato: String?,
    turni: List<Turno>,
    colleghi: List<Collega>,
    registro: List<EventoRegistro>,
    onApplica: () -> Unit,
    onVerifica: () -> Unit,
    onDisattiva: () -> Unit,
    onImporta: () -> Unit,
    onProva: () -> Unit,
    onCambio: (Turno?) -> Unit,
    onTuttiITurni: () -> Unit,
    onTuttoIlRegistro: () -> Unit,
    onImpostazioni: () -> Unit,
) {
    val context = LocalContext.current
    val giro = ricontrollaAlRitorno()
    val mancanti = remember(giro) { Configurazione.controlli(context).count { !it.ok } }
    var confermaDisattiva by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Deviazione Reperibilità", style = MaterialTheme.typography.titleLarge)
                        situazione?.let {
                            Text(
                                "Numero deviato: ${Nomi.formattaTelefono(it.preferenze.numeroReperibilita)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (mancanti > 0) {
                item {
                    Card(
                        onClick = onImpostazioni,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Completa la configurazione", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (mancanti == 1) "Manca 1 impostazione per il funzionamento automatico"
                                    else "Mancano $mancanti impostazioni per il funzionamento automatico",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (situazione != null) CardStato(situazione, occupato) else Box(Modifier.height(240.dp))
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulsanteGrande("Applica ora", Icons.Rounded.PhoneForwarded, Modifier.weight(1f), principale = true, onClick = onApplica)
                    PulsanteGrande("Verifica stato", Icons.Rounded.VerifiedUser, Modifier.weight(1f), onClick = onVerifica)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulsanteGrande("Disattiva", Icons.Rounded.PhoneDisabled, Modifier.weight(1f), contorno = true) {
                        confermaDisattiva = true
                    }
                    PulsanteGrande("Importa Excel", Icons.Rounded.UploadFile, Modifier.weight(1f), onClick = onImporta)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onCambio(situazione?.turno) }) {
                        Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cambio turno")
                    }
                    TextButton(onClick = onProva) {
                        Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Prova deviazione")
                    }
                }
            }

            val ora = situazione?.ora ?: System.currentTimeMillis()
            val prossimi = Calendario.prossimi(turni, ora, 6)
            item {
                TitoloSezione("Prossimi turni") {
                    if (turni.isNotEmpty()) TextButton(onClick = onTuttiITurni) { Text("Tutti") }
                }
            }
            if (prossimi.isEmpty()) {
                item { Vuoto("Nessun turno caricato. Importa il file Excel dei turni.", Icons.Rounded.UploadFile) }
            } else {
                val corrente = situazione?.turno
                itemsIndexed(prossimi, key = { _, t -> "t${t.id}" }) { i, t ->
                    if (i == 0 || Formati.zdt(t.inizio).toLocalDate() != Formati.zdt(prossimi[i - 1].inizio).toLocalDate()) {
                        IntestazioneGiorno(t.inizio)
                    }
                    val info = infoRiga(turni, t, colleghi)
                    RigaTimeline(
                        t, Nomi.trova(t.nome, colleghi), inCorso = t.id == corrente?.id,
                        primo = i == 0, ultimo = i == prossimi.lastIndex,
                        sostituitoDa = info.sostituitoDa,
                        notaParziale = info.notaParziale,
                    )
                }
            }

            item {
                TitoloSezione("Registro") {
                    if (registro.isNotEmpty()) TextButton(onClick = onTuttoIlRegistro) { Text("Tutto") }
                }
            }
            if (registro.isEmpty()) {
                item { Vuoto("Ancora nessuna deviazione registrata.", Icons.Rounded.PhoneCallback) }
            } else {
                items(registro.take(5), key = { "r${it.id}" }) { RigaRegistro(it) }
            }
        }
    }

    if (confermaDisattiva) {
        AlertDialog(
            onDismissRequest = { confermaDisattiva = false },
            icon = { Icon(Icons.Rounded.PhoneDisabled, null) },
            title = { Text("Disattivare la deviazione?") },
            text = {
                Text(
                    "Le chiamate torneranno a squillare su questo telefono. L'automatismo verrà sospeso " +
                        "finché non premi \"Applica ora\".",
                )
            },
            confirmButton = {
                Button(onClick = { confermaDisattiva = false; onDisattiva() }) { Text("Disattiva") }
            },
            dismissButton = { TextButton(onClick = { confermaDisattiva = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun PulsanteGrande(
    testo: String,
    icona: ImageVector,
    modifier: Modifier = Modifier,
    principale: Boolean = false,
    contorno: Boolean = false,
    onClick: () -> Unit,
) {
    val m = modifier.height(64.dp)
    val forma = RoundedCornerShape(20.dp)
    val contenuto: @Composable () -> Unit = {
        Icon(icona, null, Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(testo, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when {
        principale -> Button(onClick = onClick, modifier = m, shape = forma) { contenuto() }
        contorno -> OutlinedButton(onClick = onClick, modifier = m, shape = forma) { contenuto() }
        else -> FilledTonalButton(onClick = onClick, modifier = m, shape = forma) { contenuto() }
    }
}

@Composable
fun Vuoto(testo: String, icona: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icona, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(testo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La grande card di stato: verde, arancione, rosso o grigio. */
@Composable
fun CardStato(s: Situazione, occupato: String?) {
    val colori = LocalTavolozza.current.per(s.livello)
    val inizio by animateColorAsState(colori.inizio, tween(600), label = "inizio")
    val fine by animateColorAsState(colori.fine, tween(600), label = "fine")
    val testo = colori.testo
    val secondario = testo.copy(alpha = 0.82f)

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(inizio, fine)))
                .padding(22.dp)
                .animateContentSize(),
        ) {
            // Stato della deviazione
            AnimatedContent(
                targetState = s.livello to s.titolo,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "titolo",
            ) { (livello, titolo) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (livello) {
                            Livello.VERDE -> Icons.Rounded.CheckCircle
                            Livello.ARANCIONE -> Icons.Rounded.HourglassTop
                            Livello.ROSSO -> Icons.Rounded.Error
                            Livello.GRIGIO -> Icons.Rounded.PauseCircle
                        },
                        null, tint = testo, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(titolo, color = testo, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(18.dp))

            // Chi è di turno
            AnimatedContent(
                targetState = s.collega to s.turno,
                transitionSpec = { (fadeIn(tween(400)) togetherWith fadeOut(tween(200))) },
                label = "persona",
            ) { (collega, turno) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (turno != null) {
                        Box(
                            Modifier.size(78.dp).background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            AvatarTurno(turno, collega, 68.dp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Di turno ora", color = secondario, style = MaterialTheme.typography.labelLarge)
                            Text(
                                collega?.let { Nomi.nomeVisualizzato(it) } ?: Nomi.capitalizza(turno.nome),
                                color = testo,
                                style = MaterialTheme.typography.headlineMedium,
                                maxLines = 2,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Phone, null, tint = secondario, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    collega?.let { Nomi.formattaTelefono(it.telefono) } ?: "Numero non in rubrica",
                                    color = testo, style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    } else {
                        Column {
                            Text("Nessuno di turno", color = testo, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                s.prossimoInizio?.let { "Il prossimo turno inizia ${Formati.quando(it, s.ora)}" }
                                    ?: "Importa il file Excel con i turni",
                                color = secondario, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // Orari e conto alla rovescia
            if (s.turno != null && s.fineBlocco != null) {
                Spacer(Modifier.height(18.dp))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = testo, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Cambio tra ${Formati.durata(s.fineBlocco - s.ora)}",
                                color = testo, style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            "Dal ${Formati.giornoOra(s.inizioBlocco ?: s.turno.inizio)} fino a ${Formati.quando(s.fineBlocco, s.ora)}",
                            color = secondario, style = MaterialTheme.typography.bodySmall,
                        )
                        s.prossimoTurno?.let { p ->
                            Text(
                                "Poi: ${s.prossimoCollega?.let { Nomi.nomeVisualizzato(it) } ?: Nomi.capitalizza(p.nome)}",
                                color = secondario, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Deviazione impostata e ultima verifica
            Spacer(Modifier.height(14.dp))
            val st = s.stato
            if (st.numero != null && st.stato != StatoDeviazione.DISATTIVATA) {
                Text(
                    "Deviazione verso ${st.nome ?: ""} ${Nomi.formattaTelefono(st.numero)} dal ${Formati.giornoOra(st.dal)}",
                    color = secondario, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (st.verificaQuando > 0) {
                val esito = when (st.verificaOk) {
                    true -> "confermata"
                    false -> "NON corrisponde"
                    null -> "senza esito"
                }
                Text(
                    "Ultima verifica operatore: ${Formati.giornoOra(st.verificaQuando)} · $esito" +
                        st.verificaTesto?.let { "\n“${it.trim().take(140)}”" }.orEmpty(),
                    color = secondario, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (s.livello == Livello.ROSSO || s.livello == Livello.ARANCIONE) {
                st.messaggio?.let {
                    Text(it, color = testo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }

            AnimatedVisibility(occupato != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 14.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = testo, trackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, null, tint = testo, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(occupato.orEmpty(), color = testo, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
