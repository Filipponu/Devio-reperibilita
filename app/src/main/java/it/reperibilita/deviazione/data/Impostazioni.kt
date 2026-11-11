package it.reperibilita.deviazione.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val NUMERO_REPERIBILITA_PREDEFINITO = "3929019431"

enum class StatoDeviazione { NESSUNA, IN_CORSO, IN_ATTESA, CONFERMATA, ERRORE, DISATTIVATA }

/** Stato della deviazione come risulta all'app (l'ultima cosa fatta e verificata). */
data class Stato(
    val numero: String?,
    val nome: String?,
    val stato: StatoDeviazione,
    val dal: Long,
    val tentativo: Long,
    val messaggio: String?,
    val verificaQuando: Long,
    val verificaTesto: String?,
    val verificaOk: Boolean?,
    val allarmeAttivo: Boolean,
    val ultimoAvvisoSenzaTurno: Long,
)

data class Preferenze(
    val numeroReperibilita: String,
    val simSubId: Int,
    val automatico: Boolean,
    val allarmeSenzaConferma: Boolean,
    val prefissoInternazionale: Boolean,
    /** true = suono d'allarme dell'app, false = suoneria della sveglia del telefono. */
    val suonoApp: Boolean = true,
)

private val Context.archivio by preferencesDataStore(name = "impostazioni")

class Impostazioni(private val context: Context) {

    private object K {
        val numeroReperibilita = stringPreferencesKey("numero_reperibilita")
        val sim = intPreferencesKey("sim_sub_id")
        val automatico = booleanPreferencesKey("automatico")
        val allarmeSenzaConferma = booleanPreferencesKey("allarme_senza_conferma")
        val prefisso = booleanPreferencesKey("prefisso_internazionale")
        val suonoApp = booleanPreferencesKey("suono_app")

        val numero = stringPreferencesKey("stato_numero")
        val nome = stringPreferencesKey("stato_nome")
        val stato = stringPreferencesKey("stato")
        val dal = longPreferencesKey("stato_dal")
        val tentativo = longPreferencesKey("stato_tentativo")
        val messaggio = stringPreferencesKey("stato_messaggio")
        val verificaQuando = longPreferencesKey("verifica_quando")
        val verificaTesto = stringPreferencesKey("verifica_testo")
        val verificaOk = stringPreferencesKey("verifica_ok")
        val allarmeAttivo = booleanPreferencesKey("allarme_attivo")
        val avvisoSenzaTurno = longPreferencesKey("avviso_senza_turno")
    }

    private fun preferenzeDa(p: Preferences) = Preferenze(
        numeroReperibilita = p[K.numeroReperibilita] ?: NUMERO_REPERIBILITA_PREDEFINITO,
        simSubId = p[K.sim] ?: -1,
        automatico = p[K.automatico] ?: true,
        allarmeSenzaConferma = p[K.allarmeSenzaConferma] ?: true,
        prefissoInternazionale = p[K.prefisso] ?: false,
        suonoApp = p[K.suonoApp] ?: true,
    )

    private fun statoDa(p: Preferences) = Stato(
        numero = p[K.numero],
        nome = p[K.nome],
        stato = p[K.stato]?.let { runCatching { StatoDeviazione.valueOf(it) }.getOrNull() } ?: StatoDeviazione.NESSUNA,
        dal = p[K.dal] ?: 0L,
        tentativo = p[K.tentativo] ?: 0L,
        messaggio = p[K.messaggio],
        verificaQuando = p[K.verificaQuando] ?: 0L,
        verificaTesto = p[K.verificaTesto],
        verificaOk = p[K.verificaOk]?.toBooleanStrictOrNull(),
        allarmeAttivo = p[K.allarmeAttivo] ?: false,
        ultimoAvvisoSenzaTurno = p[K.avvisoSenzaTurno] ?: 0L,
    )

    val preferenze: Flow<Preferenze> = context.archivio.data.map(::preferenzeDa)
    val stato: Flow<Stato> = context.archivio.data.map(::statoDa)

    suspend fun preferenzeOra(): Preferenze = preferenze.first()
    suspend fun statoOra(): Stato = stato.first()

    private suspend fun modifica(blocco: (MutablePreferences) -> Unit) {
        context.archivio.edit { blocco(it) }
    }

    suspend fun impostaNumeroReperibilita(numero: String) = modifica { it[K.numeroReperibilita] = numero }
    suspend fun impostaSim(subId: Int) = modifica { it[K.sim] = subId }
    suspend fun impostaAutomatico(attivo: Boolean) = modifica { it[K.automatico] = attivo }
    suspend fun impostaAllarmeSenzaConferma(attivo: Boolean) = modifica { it[K.allarmeSenzaConferma] = attivo }
    suspend fun impostaPrefisso(attivo: Boolean) = modifica { it[K.prefisso] = attivo }
    suspend fun impostaSuonoApp(attivo: Boolean) = modifica { it[K.suonoApp] = attivo }
    suspend fun impostaAllarmeAttivo(attivo: Boolean) = modifica { it[K.allarmeAttivo] = attivo }
    suspend fun impostaAvvisoSenzaTurno(quando: Long) = modifica { it[K.avvisoSenzaTurno] = quando }

    /** Inizia un nuovo tentativo di deviazione verso [numero]. */
    suspend fun iniziaTentativo(numero: String, nome: String, quando: Long) = modifica {
        it[K.numero] = numero
        it[K.nome] = nome
        it[K.stato] = StatoDeviazione.IN_CORSO.name
        it[K.tentativo] = quando
        it[K.dal] = quando
        it.remove(K.messaggio)
    }

    suspend fun impostaStato(stato: StatoDeviazione, messaggio: String?) = modifica {
        it[K.stato] = stato.name
        if (messaggio != null) it[K.messaggio] = messaggio else it.remove(K.messaggio)
    }

    /** Deviazione disattivata: nessun numero attivo. */
    suspend fun impostaDisattivata(messaggio: String?, quando: Long) = modifica {
        it.remove(K.numero)
        it.remove(K.nome)
        it[K.stato] = StatoDeviazione.DISATTIVATA.name
        it[K.dal] = quando
        if (messaggio != null) it[K.messaggio] = messaggio else it.remove(K.messaggio)
    }

    /** Stesso numero ma turno diverso: aggiorna solo il nome mostrato. */
    suspend fun aggiornaNome(nome: String) = modifica { it[K.nome] = nome }

    suspend fun registraVerifica(quando: Long, testo: String?, ok: Boolean?) = modifica {
        it[K.verificaQuando] = quando
        if (testo != null) it[K.verificaTesto] = testo else it.remove(K.verificaTesto)
        if (ok != null) it[K.verificaOk] = ok.toString() else it.remove(K.verificaOk)
    }
}
