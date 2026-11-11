package it.reperibilita.deviazione.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.BuildConfig
import it.reperibilita.deviazione.data.Preferenze
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.telefonia.Sim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataImpostazioni(
    preferenze: Preferenze?,
    simDisponibili: () -> List<Sim>,
    onNumero: (String) -> Unit,
    onSim: (Int) -> Unit,
    onAutomatico: (Boolean) -> Unit,
    onAllarmeSenzaConferma: (Boolean) -> Unit,
    onPrefisso: (Boolean) -> Unit,
    onSuonoApp: (Boolean) -> Unit,
    onProvaDeviazione: () -> Unit,
    onProvaAllarme: () -> Unit,
) {
    val context = LocalContext.current
    var giro by remember { mutableIntStateOf(0) }
    val ritorno = ricontrollaAlRitorno()
    val controlli = remember(ritorno, giro) { Configurazione.controlli(context) }
    val sim = remember(ritorno, giro) { simDisponibili() }
    val permessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        giro++
        if (it.values.any { ok -> !ok }) Configurazione.apriDettagliApp(context)
    }
    var modificaNumero by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Impostazioni") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item { TitoloSezione("Configurazione") }
            items(controlli, key = { it.voce.name }) { c ->
                RigaControllo(c) {
                    when (c.voce) {
                        Voce.TELEFONO -> permessi.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                        Voce.NOTIFICHE ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !Configurazione.concesso(context, Manifest.permission.POST_NOTIFICATIONS)
                            ) permessi.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            else Configurazione.apriNotifiche(context)
                        Voce.SCHERMO_INTERO -> Configurazione.apriSchermoIntero(context)
                        Voce.ALLARMI_ESATTI -> Configurazione.apriAllarmiEsatti(context)
                        Voce.BATTERIA -> Configurazione.chiediBatteria(context)
                    }
                }
            }
            item { GuidaSamsung() }

            item { TitoloSezione("Deviazione") }
            item {
                ListItem(
                    headlineContent = { Text("Numero di reperibilità (questa SIM)") },
                    supportingContent = { Text(preferenze?.numeroReperibilita?.let { Nomi.formattaTelefono(it) } ?: "") },
                    trailingContent = { IconButton(onClick = { modificaNumero = true }) { Icon(Icons.Rounded.Edit, "Modifica") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (sim.size > 1) {
                item {
                    Text(
                        "SIM da usare per la deviazione",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                items(sim, key = { "sim${it.subId}" }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = preferenze?.simSubId == s.subId, onClick = { onSim(s.subId) })
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = preferenze?.simSubId == s.subId, onClick = { onSim(s.subId) })
                        Icon(Icons.Rounded.SimCard, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.etichetta)
                    }
                }
                if (preferenze?.simSubId == -1) {
                    item {
                        Text(
                            "Nessuna SIM scelta: viene usata la SIM predefinita per le chiamate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
            item {
                Interruttore(
                    "Deviazione automatica a ogni cambio turno",
                    "Se la disattivi, l'app non invia più codici finché non la riattivi o premi \"Applica ora\".",
                    preferenze?.automatico ?: true, onAutomatico,
                )
            }
            item {
                Interruttore(
                    "Allarme se l'operatore non conferma",
                    "Fa suonare l'allarme se dopo 2 minuti la deviazione non risulta confermata.",
                    preferenze?.allarmeSenzaConferma ?: true, onAllarmeSenzaConferma,
                )
            }
            item {
                Interruttore(
                    "Usa il prefisso +39 nel codice",
                    "Attivalo solo se l'operatore rifiuta il numero senza prefisso.",
                    preferenze?.prefissoInternazionale ?: false, onPrefisso,
                )
            }

            item { TitoloSezione("Allarme") }
            item {
                Interruttore(
                    "Usa il suono di allarme dell'app",
                    "Bip rapidi e forti, sempre uguali. Se lo disattivi suona la suoneria della sveglia del telefono. " +
                        "In entrambi i casi suona anche in silenzioso e con Non disturbare.",
                    preferenze?.suonoApp ?: true, onSuonoApp,
                )
            }

            item { TitoloSezione("Prove") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onProvaDeviazione, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Prova deviazione")
                    }
                    OutlinedButton(onClick = onProvaAllarme, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Prova allarme")
                    }
                }
            }

            item { TitoloSezione("Informazioni") }
            item {
                Text(
                    "Versione ${BuildConfig.VERSION_NAME}\n" +
                        "Tutti i dati (turni, rubrica, registro) restano su questo telefono: nessun server esterno.\n" +
                        "Codici usati: **21*numero# per attivare, ##21# per disattivare, *#21# per verificare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (modificaNumero) {
        var testo by remember { mutableStateOf(preferenze?.numeroReperibilita.orEmpty()) }
        AlertDialog(
            onDismissRequest = { modificaNumero = false },
            title = { Text("Numero di reperibilità") },
            text = {
                Column {
                    Text("È il numero di questa SIM, quello che i colleghi e i pazienti chiamano.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(testo, { testo = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                }
            },
            confirmButton = {
                Button(enabled = testo.count(Char::isDigit) >= 8, onClick = { onNumero(testo); modificaNumero = false }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { modificaNumero = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun RigaControllo(c: Controllo, onAzione: () -> Unit) {
    val t = LocalTavolozza.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (c.ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null,
                tint = if (c.ok) t.verde.inizio else t.arancione.inizio,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.titolo, style = MaterialTheme.typography.titleSmall)
                Text(c.descrizione, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!c.ok) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAzione) { Text("Attiva") }
            }
        }
    }
}

@Composable
private fun GuidaSamsung() {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BatteryChargingFull, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (Configurazione.eSamsung()) "Samsung: \"App mai in sospensione\"" else "Risparmio energetico del produttore",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "1. Impostazioni → Assistenza dispositivo e batteria → Batteria\n" +
                    "2. Limiti utilizzo in background → App mai in sospensione\n" +
                    "3. Tocca + e scegli \"Deviazione Reperibilità\"\n" +
                    "4. Poi Impostazioni → App → Deviazione Reperibilità → Batteria → \"Senza restrizioni\"\n\n" +
                    "Controlla anche che l'app NON sia tra le \"App in sospensione\" o \"App in sospensione profonda\".",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { Configurazione.apriBatteriaSamsung(context) }) { Text("Apri Batteria") }
                OutlinedButton(onClick = { Configurazione.apriDettagliApp(context) }) { Text("Info app") }
            }
        }
    }
}

@Composable
private fun Interruttore(titolo: String, descrizione: String, valore: Boolean, onCambia: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(titolo) },
        supportingContent = { Text(descrizione) },
        trailingContent = { Switch(checked = valore, onCheckedChange = onCambia) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = valore, onClick = { onCambia(!valore) }),
    )
}
