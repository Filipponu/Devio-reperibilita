package it.reperibilita.deviazione.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione
import it.reperibilita.deviazione.telefonia.InterpreteRisposta

/** "Prova deviazione": chiamata da un altro telefono, oppure verifica con *#21#. */
@Composable
fun DialogProvaDeviazione(
    s: Situazione,
    occupato: String?,
    onEsito: (Boolean) -> Unit,
    onVerifica: () -> Unit,
    onChiudi: () -> Unit,
) {
    val numero = s.stato.numero ?: s.numeroAtteso
    val nome = s.stato.nome ?: s.collega?.cognome
    val grassetto = SpanStyle(fontWeight = FontWeight.Bold)
    AlertDialog(
        onDismissRequest = onChiudi,
        icon = { Icon(Icons.Rounded.Science, null) },
        title = { Text("Prova deviazione") },
        text = {
            Column {
                Text(buildAnnotatedString {
                    append("1. Da un altro telefono chiama il ")
                    withStyle(grassetto) { append(Nomi.formattaTelefono(s.preferenze.numeroReperibilita)) }
                    append(".\n\n2. Deve squillare il telefono di ")
                    withStyle(grassetto) { append(nome ?: "collega di turno") }
                    if (numero != null) append(" (${Nomi.formattaTelefono(numero)})")
                    append(".\n\nLa chiamata è arrivata al collega di turno?")
                })
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("In alternativa", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Chiedi all'operatore lo stato con *#21# e confronta il numero restituito con quello atteso.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onVerifica, enabled = occupato == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verifica con *#21#")
                }
                if (occupato != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text(occupato, style = MaterialTheme.typography.bodySmall)
                } else if (s.stato.verificaQuando > 0) {
                    val restituiti = s.stato.verificaTesto?.let { InterpreteRisposta.numeri(it) }.orEmpty()
                    val atteso = s.numeroAtteso ?: numero
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Verifica delle ${Formati.ora(s.stato.verificaQuando)} (${Formati.giorno(s.stato.verificaQuando)})",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "Numero restituito: " + (restituiti.joinToString { Nomi.formattaTelefono(it) }.ifEmpty { "nessuno" }) +
                            "\nNumero atteso: " + (atteso?.let { Nomi.formattaTelefono(it) } ?: "nessuno") +
                            "\n" + when {
                                atteso != null && restituiti.any { Nomi.stessoNumero(it, atteso) } -> "✓ Corrispondono"
                                restituiti.isEmpty() && s.stato.verificaOk == null -> "Risposta: ${s.stato.verificaTesto.orEmpty()}"
                                else -> "✗ Non corrispondono"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onEsito(true); onChiudi() }) { Text("Sì, è arrivata") } },
        dismissButton = {
            Column {
                TextButton(onClick = { onEsito(false); onChiudi() }) { Text("No, non è arrivata") }
                TextButton(onClick = onChiudi) { Text("Chiudi") }
            }
        },
    )
}
