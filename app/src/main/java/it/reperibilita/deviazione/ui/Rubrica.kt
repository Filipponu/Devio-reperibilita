package it.reperibilita.deviazione.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.dominio.Nomi

/** Selettore di sistema di un numero dalla rubrica del telefono (nessun permesso necessario). */
class ScegliNumero : ActivityResultContract<Unit, Pair<String, String>?>() {
    private lateinit var context: Context

    override fun createIntent(context: Context, input: Unit): Intent {
        this.context = context.applicationContext
        return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<String, String>? {
        val uri: Uri = intent?.data ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
        }.getOrNull()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataRubrica(colleghi: List<Collega>, onSalva: (Collega) -> Unit, onElimina: (Collega) -> Unit) {
    var modifica by remember { mutableStateOf<Collega?>(null) }
    var nuovo by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Rubrica") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nuovo = true },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                text = { Text("Aggiungi") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            item {
                Text(
                    "Il cognome deve essere scritto come compare nel file Excel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(colleghi, key = { it.id }) { c ->
                Surface(
                    onClick = { modifica = c },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Nomi.iniziali(c), c.cognome, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.cognome.uppercase(), style = MaterialTheme.typography.titleMedium)
                            if (c.nome.isNotBlank()) Text(Nomi.capitalizza(c.nome), style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Phone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    Nomi.formattaTelefono(c.telefono),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (nuovo) {
        DialogCollega(null, onSalva = { onSalva(it); nuovo = false }, onElimina = null, onChiudi = { nuovo = false })
    }
    modifica?.let { c ->
        DialogCollega(c, onSalva = { onSalva(it); modifica = null }, onElimina = { onElimina(c); modifica = null }, onChiudi = { modifica = null })
    }
}

/** Aggiunta / modifica di un collega, con importazione del numero dai contatti. */
@Composable
fun DialogCollega(
    iniziale: Collega?,
    onSalva: (Collega) -> Unit,
    onElimina: (() -> Unit)?,
    onChiudi: () -> Unit,
    titolo: String = if (iniziale == null || iniziale.id == 0L) "Nuovo collega" else "Modifica collega",
) {
    var cognome by remember { mutableStateOf(iniziale?.cognome.orEmpty()) }
    var nome by remember { mutableStateOf(iniziale?.nome.orEmpty()) }
    var telefono by remember { mutableStateOf(iniziale?.telefono.orEmpty()) }
    var confermaElimina by remember { mutableStateOf(false) }
    val contatti = rememberLauncherForActivityResult(ScegliNumero()) { scelto ->
        scelto ?: return@rememberLauncherForActivityResult
        telefono = Nomi.pulisciTelefono(scelto.second).let { if (it.startsWith("+39")) it.drop(3) else it }
        if (cognome.isBlank() && scelto.first.isNotBlank()) {
            val parti = scelto.first.trim().split(Regex("\\s+"))
            cognome = parti.last().uppercase()
            nome = parti.dropLast(1).joinToString(" ")
        }
    }
    val cifre = telefono.count(Char::isDigit)
    val valido = cognome.isNotBlank() && cifre >= 8

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    cognome, { cognome = it.uppercase() },
                    label = { Text("Cognome (come nell'Excel)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    nome, { nome = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    telefono, { telefono = it },
                    label = { Text("Numero di telefono") },
                    singleLine = true,
                    isError = telefono.isNotEmpty() && cifre < 8,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                FilledTonalButton(onClick = { runCatching { contatti.launch(Unit) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Contacts, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Prendi dai contatti")
                }
                if (onElimina != null) {
                    TextButton(onClick = { confermaElimina = true }) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Elimina dalla rubrica", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valido,
                onClick = { onSalva(Collega(id = iniziale?.id ?: 0L, cognome = cognome.trim(), nome = nome.trim(), telefono = telefono)) },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
    if (confermaElimina && onElimina != null) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare ${cognome}?") },
            text = { Text("Se compare nei turni, l'app ti chiederà di nuovo il numero.") },
            confirmButton = { Button(onClick = { confermaElimina = false; onElimina() }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } },
        )
    }
}
