package it.reperibilita.deviazione.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.reperibilita.deviazione.allarme.AllarmeService
import it.reperibilita.deviazione.contenitore
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Esito
import it.reperibilita.deviazione.data.EventoRegistro
import it.reperibilita.deviazione.data.Preferenze
import it.reperibilita.deviazione.data.TipoEvento
import it.reperibilita.deviazione.data.Turno
import it.reperibilita.deviazione.dominio.CalcolaSituazione
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione
import it.reperibilita.deviazione.excel.Anteprima
import it.reperibilita.deviazione.excel.FileExcelNonValido
import it.reperibilita.deviazione.excel.LettoreTurni
import it.reperibilita.deviazione.excel.PreparaAnteprima
import it.reperibilita.deviazione.excel.XlsxReader
import it.reperibilita.deviazione.servizio.DeviazioneService
import it.reperibilita.deviazione.telefonia.Sim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stato dell'importazione di un file Excel. */
sealed interface StatoImport {
    data object Nessuno : StatoImport
    data object Lettura : StatoImport
    data class Pronta(val anteprima: Anteprima) : StatoImport
    data class Errore(val messaggio: String) : StatoImport
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.contenitore

    private val orologio = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val turni: StateFlow<List<Turno>> = c.db.turni().osserva().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val colleghi: StateFlow<List<Collega>> = c.db.colleghi().osserva().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val registro: StateFlow<List<EventoRegistro>> = c.db.registro().osserva(500).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferenze: StateFlow<Preferenze?> = c.impostazioni.preferenze.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val occupato: StateFlow<String?> = c.motore.occupato

    val situazione: StateFlow<Situazione?> = combine(
        c.db.turni().osserva(), c.db.colleghi().osserva(), c.impostazioni.stato, c.impostazioni.preferenze, orologio,
    ) { t, col, st, pref, ora -> CalcolaSituazione.calcola(t, col, st, pref, ora) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messaggi = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Messaggi brevi da mostrare in uno snackbar. */
    val messaggi = _messaggi.asSharedFlow()

    private val _import = MutableStateFlow<StatoImport>(StatoImport.Nessuno)
    val importazione: StateFlow<StatoImport> = _import.asStateFlow()

    fun messaggio(testo: String) {
        _messaggi.tryEmit(testo)
    }

    /** All'apertura: riprogramma gli allarmi e applica subito la deviazione se un cambio è stato saltato. */
    fun allApertura() = viewModelScope.launch {
        c.pianificatore.programma()
        c.motore.aggiornaNotificheEWidget()
        if (c.motore.serveRiapplicare()) DeviazioneService.avvia(getApplication(), DeviazioneService.APPLICA)
    }

    fun applicaOra() {
        val s = situazione.value
        if (s != null && s.turno == null) {
            messaggio("Nessuno è di turno in questo momento: non c'è niente da applicare")
            return
        }
        avvia(DeviazioneService.APPLICA_ORA)
    }
    fun verifica() = avvia(DeviazioneService.VERIFICA)
    fun disattiva() = avvia(DeviazioneService.DISATTIVA)

    private fun avvia(azione: String) {
        if (occupato.value != null) {
            messaggio("Attendi: un'operazione è già in corso")
            return
        }
        DeviazioneService.avvia(getApplication(), azione)
    }

    fun esitoProva(riuscita: Boolean) = viewModelScope.launch {
        c.motore.esitoProvaChiamata(riuscita)
        messaggio(if (riuscita) "Prova registrata: deviazione riuscita" else "Prova registrata: deviazione NON riuscita")
    }

    fun provaAllarme() = AllarmeService.avvia(getApplication(), "Questa è solo una prova.", prova = true)

    // --- Cambi turno manuali ---

    fun salvaCambio(collega: Collega, inizio: Long, fine: Long, sostituito: Turno?) = viewModelScope.launch {
        val nome = Nomi.pulisciNomeExcel("${collega.cognome} ${collega.nome}")
        val mese = java.time.YearMonth.from(Formati.zdt(inizio)).toString()
        c.db.turni().inserisciUno(Turno(inizio = inizio, fine = fine, nome = nome, codice = "M", mese = mese, manuale = true))
        val dettaglio = "${collega.cognome} dal ${Formati.giornoOra(inizio)} al ${Formati.giornoOra(fine)}" +
            (sostituito?.let { " al posto di ${Nomi.trova(it.nome, colleghi.value)?.cognome ?: it.nome}" } ?: "")
        c.motore.registra(TipoEvento.CAMBIO_TURNO, Esito.INFO, collega.telefono, collega.cognome, dettaglio)
        dopoModificaTurni()
        messaggio("Cambio salvato: $dettaglio")
    }

    fun eliminaCambio(turno: Turno) = viewModelScope.launch {
        c.db.turni().elimina(turno)
        c.motore.registra(
            TipoEvento.CAMBIO_TURNO, Esito.INFO, null, null,
            "Annullato il cambio ${turno.nome} dal ${Formati.giornoOra(turno.inizio)} al ${Formati.giornoOra(turno.fine)}",
        )
        dopoModificaTurni()
        messaggio("Cambio annullato: torna a valere il file Excel")
    }

    /** Dopo ogni modifica dei turni: riprogramma e, se è cambiato chi è di turno adesso, applica subito. */
    private suspend fun dopoModificaTurni() {
        c.pianificatore.programma()
        c.motore.aggiornaNotificheEWidget()
        if (c.motore.serveRiapplicare()) DeviazioneService.avvia(getApplication(), DeviazioneService.APPLICA)
    }

    // --- Rubrica ---

    fun salvaCollega(collega: Collega) = viewModelScope.launch {
        c.db.colleghi().salva(
            collega.copy(
                cognome = Nomi.pulisciNomeExcel(collega.cognome),
                nome = Nomi.capitalizza(collega.nome.trim()),
                telefono = Nomi.pulisciTelefono(collega.telefono),
            ),
        )
        c.motore.aggiornaNotificheEWidget()
        aggiornaAnteprima()
    }

    fun eliminaCollega(collega: Collega) = viewModelScope.launch {
        c.db.colleghi().elimina(collega)
        c.motore.aggiornaNotificheEWidget()
        messaggio("${collega.cognome} eliminato dalla rubrica")
    }

    // --- Impostazioni ---

    fun impostaNumeroReperibilita(numero: String) = viewModelScope.launch { c.impostazioni.impostaNumeroReperibilita(Nomi.pulisciTelefono(numero)) }
    fun impostaSim(subId: Int) = viewModelScope.launch { c.impostazioni.impostaSim(subId) }
    fun impostaAutomatico(attivo: Boolean) = viewModelScope.launch {
        c.impostazioni.impostaAutomatico(attivo)
        if (attivo && c.motore.serveRiapplicare()) DeviazioneService.avvia(getApplication(), DeviazioneService.APPLICA)
        c.motore.aggiornaNotificheEWidget()
    }
    fun impostaAllarmeSenzaConferma(attivo: Boolean) = viewModelScope.launch { c.impostazioni.impostaAllarmeSenzaConferma(attivo) }
    fun impostaPrefisso(attivo: Boolean) = viewModelScope.launch { c.impostazioni.impostaPrefisso(attivo) }
    fun impostaSuonoApp(attivo: Boolean) = viewModelScope.launch { c.impostazioni.impostaSuonoApp(attivo) }
    fun impostaSmsCambio(attivo: Boolean) = viewModelScope.launch { c.impostazioni.impostaSmsCambio(attivo) }
    fun impostaNumeroAvvisi(numero: String) = viewModelScope.launch { c.impostazioni.impostaNumeroAvvisi(Nomi.pulisciTelefono(numero)) }
    fun simDisponibili(): List<Sim> = c.telefonia.simDisponibili()
    fun puoProgrammareEsatti(): Boolean = c.pianificatore.puoProgrammareEsatti()

    // --- Importazione Excel ---

    fun caricaExcel(uri: Uri) = viewModelScope.launch {
        _import.value = StatoImport.Lettura
        val app = getApplication<Application>()
        _import.value = try {
            val nome = withContext(Dispatchers.IO) { nomeFile(uri) }
            val fogli = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { XlsxReader.leggi(it) }
                    ?: throw FileExcelNonValido("Impossibile aprire il file")
            }
            val letti = withContext(Dispatchers.Default) { LettoreTurni.leggi(fogli) }
            val anteprima = PreparaAnteprima.prepara(nome, letti, c.db.turni().tutti(), c.db.colleghi().tutti(), System.currentTimeMillis())
            StatoImport.Pronta(anteprima)
        } catch (e: FileExcelNonValido) {
            StatoImport.Errore(e.message ?: "File non valido")
        } catch (e: Exception) {
            StatoImport.Errore("Errore nella lettura del file: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun aggiornaAnteprima() {
        val stato = _import.value as? StatoImport.Pronta ?: return
        val a = stato.anteprima
        _import.value = StatoImport.Pronta(
            a.copy(nomiSconosciuti = PreparaAnteprima.sconosciuti(a.turniDaSalvare, c.db.colleghi().tutti(), System.currentTimeMillis())),
        )
    }

    fun annullaImport() {
        _import.value = StatoImport.Nessuno
    }

    fun salvaImport(mantieniCambi: Boolean, onFatto: () -> Unit) = viewModelScope.launch {
        val a = (_import.value as? StatoImport.Pronta)?.anteprima ?: return@launch
        val ora = System.currentTimeMillis()
        c.db.turni().sostituisciMesi(a.mesiDaSostituire, a.turniDaSalvare, eliminaManuali = !mantieniCambi)
        c.db.turni().eliminaPrimaDi(ora - 62L * 24 * 3_600_000)
        c.db.registro().eliminaPrimaDi(ora - 400L * 24 * 3_600_000)
        val mesi = a.mesi.filter { it.daImportare }.joinToString { it.foglio.nomeFoglio }
        val problemi = buildList {
            if (a.analisi.buchi.isNotEmpty()) add("${a.analisi.buchi.size} buchi")
            if (a.analisi.sovrapposizioni.isNotEmpty()) add("${a.analisi.sovrapposizioni.size} sovrapposizioni")
        }.joinToString(", ")
        c.motore.registra(
            TipoEvento.IMPORTAZIONE, if (problemi.isEmpty()) Esito.INFO else Esito.ERRORE, null, null,
            "${a.turniDaSalvare.size} turni ($mesi) da ${a.nomeFile}" + if (problemi.isNotEmpty()) " – $problemi" else "",
        )
        c.impostazioni.impostaAvvisoSenzaTurno(0)
        dopoModificaTurni()
        _import.value = StatoImport.Nessuno
        val fino = a.analisi.fineTurni?.let { " · turni fino a ${Formati.giornoOra(it)}" }.orEmpty()
        messaggio("Salvati ${a.turniDaSalvare.size} turni$fino")
        onFatto()
    }

    private fun nomeFile(uri: Uri): String {
        val app = getApplication<Application>()
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "file.xlsx"
    }
}
