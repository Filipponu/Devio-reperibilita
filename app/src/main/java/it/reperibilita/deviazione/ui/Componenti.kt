package it.reperibilita.deviazione.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.TipoTurno

/** Avatar rotondo con le iniziali, colore stabile per persona. */
@Composable
fun Avatar(iniziali: String, chiave: String, dimensione: Dp = 44.dp, modifier: Modifier = Modifier) {
    val colore = ColoriAvatar[Nomi.indiceColore(chiave, ColoriAvatar.size)]
    Box(
        modifier
            .size(dimensione)
            .clip(CircleShape)
            .background(colore),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iniziali,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (dimensione.value * 0.38f).sp,
        )
    }
}

@Composable
fun AvatarTurno(turno: Turno, collega: Collega?, dimensione: Dp = 40.dp) {
    if (collega != null) Avatar(Nomi.iniziali(collega), collega.cognome, dimensione)
    else Avatar(Nomi.inizialiDaNomeExcel(turno.nome), turno.nome.substringBefore(' '), dimensione)
}

/** Icona del tipo di turno: sole (giorno), luna (notte), "24" (24 ore). */
@Composable
fun IconaTurno(tipo: TipoTurno?, dimensione: Dp = 36.dp) {
    val t = LocalTavolozza.current
    val colore = when (tipo) {
        TipoTurno.GIORNO -> t.giorno
        TipoTurno.NOTTE -> t.notte
        TipoTurno.H24 -> t.h24
        null -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .size(dimensione)
            .clip(CircleShape)
            .background(colore.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        when (tipo) {
            TipoTurno.GIORNO -> Icon(Icons.Rounded.WbSunny, "Giorno", tint = colore, modifier = Modifier.size(dimensione * 0.6f))
            TipoTurno.NOTTE -> Icon(Icons.Rounded.Bedtime, "Notte", tint = colore, modifier = Modifier.size(dimensione * 0.6f))
            else -> Text("24", color = colore, fontWeight = FontWeight.Black, fontSize = (dimensione.value * 0.36f).sp)
        }
    }
}

/** Una riga della timeline verticale dei turni. */
@Composable
fun RigaTimeline(
    turno: Turno,
    collega: Collega?,
    inCorso: Boolean,
    primo: Boolean,
    ultimo: Boolean,
) {
    val tipo = TipoTurno.daCodice(turno.codice)
    val linea = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(
            Modifier.width(52.dp).padding(top = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(Formati.ora(turno.inizio), style = MaterialTheme.typography.labelLarge)
            Text(
                Formati.ora(turno.fine),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).height(10.dp).background(if (primo) Color.Transparent else linea))
                IconaTurno(tipo)
                Box(Modifier.width(2.dp).weight(1f).background(if (ultimo) Color.Transparent else linea))
            }
        }
        Surface(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            shape = MaterialTheme.shapes.medium,
            color = if (inCorso) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (inCorso) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            tonalElevation = if (inCorso) 2.dp else 0.dp,
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarTurno(turno, collega, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        collega?.let { Nomi.nomeVisualizzato(it) } ?: turno.nome,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(tipo?.etichetta ?: turno.codice)
                            if (turno.codice.endsWith("D") && turno.codice != "D") append(" · festivo")
                            append(" · ")
                            append(collega?.let { Nomi.formattaTelefono(it.telefono) } ?: "numero mancante")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            collega == null -> MaterialTheme.colorScheme.error
                            inCorso -> androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (inCorso) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(
                            "ORA",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** Intestazione di un giorno nella timeline. */
@Composable
fun IntestazioneGiorno(millis: Long) {
    Text(
        Formati.giornoLungo(millis),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Titolo di sezione con eventuale azione a destra. */
@Composable
fun TitoloSezione(titolo: String, azione: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(titolo, style = MaterialTheme.typography.titleLarge)
        azione?.invoke()
    }
}
