package it.reperibilita.deviazione.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.TipoTurno
import it.reperibilita.deviazione.dominio.ZONA_ROMA
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

private enum class Campo { INIZIO, FINE }

/**
 * Cambio turno fatto a mano, senza toccare l'Excel.
 * Con [turno] sostituisce quel turno (tutto o in parte); senza, aggiunge un turno nuovo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogCambioTurno(
    turno: Turno?,
    colleghi: List<Collega>,
    ora: Long,
    onSalva: (collega: Collega, inizio: Long, fine: Long) -> Unit,
    onChiudi: () -> Unit,
) {
    val minuto = ora - ora % 60_000
    var inizio by remember {
        mutableLongStateOf(
            turno?.let { maxOf(it.inizio, minuto) }
                ?: TipoTurno.GIORNO.intervallo(Formati.zdt(ora).toLocalDate().plusDays(1)).first.toInstant().toEpochMilli(),
        )
    }
    var fine by remember {
        mutableLongStateOf(
            turno?.fine ?: TipoTurno.GIORNO.intervallo(Formati.zdt(ora).toLocalDate().plusDays(1)).second.toInstant().toEpochMilli(),
        )
    }
    val sostituito = turno?.let { Nomi.trova(it.nome, colleghi) }
    var scelto by remember { mutableStateOf<Collega?>(null) }
    var dataDa by remember { mutableStateOf<Campo?>(null) }
    var oraDa by remember { mutableStateOf<Campo?>(null) }

    val errore = when {
        fine <= inizio -> "La fine deve essere dopo l'inizio."
        fine <= ora -> "Il cambio deve riguardare un periodo non ancora finito."
        else -> null
    }

    fun imposta(campo: Campo, nuovo: Long) {
        if (campo == Campo.INIZIO) {
            val durata = fine - inizio
            inizio = nuovo
            if (turno == null && durata > 0) fine = nuovo + durata
        } else {
            fine = nuovo
        }
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        icon = { Icon(Icons.Rounded.SwapHoriz, null) },
        title = { Text(if (turno != null) "Cambio turno" else "Aggiungi turno") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (turno != null) {
                    Text(
                        "Al posto di ${sostituito?.let { Nomi.nomeVisualizzato(it) } ?: turno.nome}\n" +
                            "${Formati.giornoOra(turno.inizio)} → ${Formati.giornoOra(turno.fine)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Puoi coprire tutto il turno o solo una parte. Il file Excel non viene modificato.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TipoTurno.entries.forEach { tipo ->
                            val giorno = Formati.zdt(inizio).toLocalDate()
                            val (a, b) = tipo.intervallo(giorno)
                            FilterChip(
                                selected = a.toInstant().toEpochMilli() == inizio && b.toInstant().toEpochMilli() == fine,
                                onClick = { inizio = a.toInstant().toEpochMilli(); fine = b.toInstant().toEpochMilli() },
                                label = { Text(tipo.etichetta) },
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                RigaOrario("Dalle", inizio, { dataDa = Campo.INIZIO }, { oraDa = Campo.INIZIO })
                RigaOrario("Alle", fine, { dataDa = Campo.FINE }, { oraDa = Campo.FINE })
                if (errore != null) {
                    Text(errore, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.size(12.dp))
                Text("Chi è reperibile", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.heightIn(max = 320.dp)) {
                    colleghi.forEach { c ->
                        val selezionato = scelto?.id == c.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selezionato, onClick = { scelto = c })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selezionato, onClick = { scelto = c })
                            Avatar(Nomi.iniziali(c), c.cognome, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Nomi.nomeVisualizzato(c), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    Nomi.formattaTelefono(c.telefono) + if (c.id == sostituito?.id) " · di turno nell'Excel" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Se il collega non è in elenco, aggiungilo prima nella Rubrica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = scelto != null && errore == null,
                onClick = { scelto?.let { onSalva(it, inizio, fine) } },
            ) { Text("Salva cambio") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )

    dataDa?.let { campo ->
        val attuale = Formati.zdt(if (campo == Campo.INIZIO) inizio else fine)
        val stato = rememberDatePickerState(
            initialSelectedDateMillis = attuale.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dataDa = null },
            confirmButton = {
                TextButton(onClick = {
                    stato.selectedDateMillis?.let { sel ->
                        val giorno = java.time.Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                        imposta(campo, unisci(giorno, attuale.toLocalTime()))
                    }
                    dataDa = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dataDa = null }) { Text("Annulla") } },
        ) { DatePicker(stato) }
    }

    oraDa?.let { campo ->
        val attuale = Formati.zdt(if (campo == Campo.INIZIO) inizio else fine)
        val stato = rememberTimePickerState(attuale.hour, attuale.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { oraDa = null },
            title = { Text(if (campo == Campo.INIZIO) "Dalle ore" else "Alle ore") },
            text = { TimeInput(stato) },
            confirmButton = {
                TextButton(onClick = {
                    imposta(campo, unisci(attuale.toLocalDate(), LocalTime.of(stato.hour, stato.minute)))
                    oraDa = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { oraDa = null }) { Text("Annulla") } },
        )
    }
}

private fun unisci(giorno: LocalDate, orario: LocalTime): Long =
    ZonedDateTime.of(giorno, orario, ZONA_ROMA).toInstant().toEpochMilli()

@Composable
private fun RigaOrario(etichetta: String, millis: Long, onData: () -> Unit, onOra: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etichetta, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        OutlinedButton(onClick = onData, modifier = Modifier.weight(1.4f)) {
            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(Formati.giorno(millis), maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onOra, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(Formati.ora(millis), maxLines = 1)
        }
    }
}
