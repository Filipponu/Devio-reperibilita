package it.reperibilita.deviazione.servizio

import android.content.Context
import it.reperibilita.deviazione.allarme.AllarmeService
import it.reperibilita.deviazione.data.AppDatabase
import it.reperibilita.deviazione.data.Collega
import it.reperibilita.deviazione.data.Esito
import it.reperibilita.deviazione.data.EventoRegistro
import it.reperibilita.deviazione.data.Impostazioni
import it.reperibilita.deviazione.data.StatoDeviazione
import it.reperibilita.deviazione.data.TipoEvento
import it.reperibilita.deviazione.dominio.CalcolaSituazione
import it.reperibilita.deviazione.dominio.Formati
import it.reperibilita.deviazione.dominio.Nomi
import it.reperibilita.deviazione.dominio.Situazione
import it.reperibilita.deviazione.notifiche.Notifiche
import it.reperibilita.deviazione.telefonia.InterpreteRisposta
import it.reperibilita.deviazione.telefonia.RisultatoMmi
import it.reperibilita.deviazione.telefonia.Sms
import it.reperibilita.deviazione.telefonia.StatoOperatore
import it.reperibilita.deviazione.telefonia.Telefonia
import it.reperibilita.deviazione.widget.WidgetStato
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Il cuore dell'app: applica la deviazione del turno in corso, la verifica con l'operatore,
 * scrive il registro, aggiorna notifiche e widget e fa partire l'allarme se qualcosa va storto.
 */
class Motore(
    private val context: Context,
    private val db: AppDatabase,
    private val impostazioni: Impostazioni,
    private val telefonia: Telefonia,
    private val sms: Sms,
    private val pianificatore: Pianificatore,
    private val notifiche: Notifiche,
) {
    private val _occupato = MutableStateFlow<String?>(null)
    /** Descrizione dell'operazione in corso (per la schermata principale), null se libero. */
    val occupato: StateFlow<String?> = _occupato.asStateFlow()

    private sealed interface EsitoVerifica {
        data class Confermata(val testo: String) : EsitoVerifica
        data class Diversa(val motivo: String) : EsitoVerifica
        data class NonVerificabile(val motivo: String) : EsitoVerifica
    }

    private fun adesso() = System.currentTimeMillis()

    suspend fun situazione(ora: Long = adesso()): Situazione = CalcolaSituazione.calcola(
        db.turni().tutti(), db.colleghi().tutti(), impostazioni.statoOra(), impostazioni.preferenzeOra(), ora,
    )

    suspend fun registra(tipo: TipoEvento, esito: Esito, numero: String?, nome: String?, dettaglio: String?) {
        db.registro().aggiungi(EventoRegistro(quando = adesso(), tipo = tipo, esito = esito, numero = numero, nome = nome, dettaglio = dettaglio))
    }

    suspend fun aggiornaNotificheEWidget() {
        notifiche.stato(situazione())
        runCatching { WidgetStato.aggiornaTutti(context) }
    }

    /** Il turno in corso richiede di (ri)applicare la deviazione? Usato all'apertura, al riavvio e al cambio d'ora. */
    suspend fun serveRiapplicare(): Boolean {
        val s = situazione()
        if (!s.preferenze.automatico || s.turno == null) return false
        if (s.collega == null) return s.stato.stato != StatoDeviazione.ERRORE
        val st = s.stato
        if (!Nomi.stessoNumero(st.numero, s.numeroAtteso)) return true
        return when (st.stato) {
            StatoDeviazione.NESSUNA, StatoDeviazione.DISATTIVATA -> true
            StatoDeviazione.IN_CORSO -> s.ora - st.tentativo > 5 * 60_000L
            else -> false
        }
    }

    /**
     * Applica la deviazione del turno in corso.
     * [forzata] = true per "Applica ora" e "Riprova": invia il codice anche se il numero non è cambiato.
     */
    suspend fun applica(forzata: Boolean) {
        pianificatore.programma()
        val pref = impostazioni.preferenzeOra()
        if (forzata && !pref.automatico) impostazioni.impostaAutomatico(true)
        if (!forzata && !pref.automatico) {
            aggiornaNotificheEWidget()
            return
        }
        if (forzata) AllarmeService.silenzia(context)

        val s = situazione()
        val turno = s.turno
        if (turno == null) {
            senzaTurno(s)
            aggiornaNotificheEWidget()
            return
        }
        val collega = s.collega
        if (collega == null) {
            fallimento(null, turno.nome, "${turno.nome} non è in rubrica: apri l'app e aggiungi il suo numero.")
            return
        }
        val st = s.stato
        val inCorsoRecente = st.stato == StatoDeviazione.IN_CORSO && s.ora - st.tentativo < 5 * 60_000L
        val giaAttiva = st.stato == StatoDeviazione.CONFERMATA || st.stato == StatoDeviazione.IN_ATTESA || inCorsoRecente
        if (!forzata && Nomi.stessoNumero(st.numero, collega.telefono) && giaAttiva) {
            // Turni consecutivi con lo stesso numero: non si rifà la deviazione.
            impostazioni.aggiornaNome(collega.cognome)
            aggiornaNotificheEWidget()
            return
        }
        attiva(collega, s.fineBlocco ?: turno.fine)
    }

    private suspend fun senzaTurno(s: Situazione) {
        val st = s.stato
        if (s.ora - st.ultimoAvvisoSenzaTurno < 6 * 3_600_000L) return
        val quando = s.prossimoInizio?.let { "Nessuno di turno fino a ${Formati.quando(it, s.ora)}." }
            ?: "Non ci sono altri turni caricati: importa il nuovo file Excel."
        val deviazione = if (st.numero != null && st.stato != StatoDeviazione.DISATTIVATA)
            "Resta attiva la deviazione verso ${st.nome} (${Nomi.formattaTelefono(st.numero)})."
        else "Nessuna deviazione attiva."
        notifiche.avviso("Nessun turno in corso", "$quando $deviazione")
        registra(TipoEvento.INFO, Esito.INFO, st.numero, st.nome, "Nessun turno in corso: deviazione lasciata invariata")
        impostazioni.impostaAvvisoSenzaTurno(s.ora)
    }

    private fun inviatoBene(r: RisultatoMmi): Boolean = when (r) {
        is RisultatoMmi.Risposta -> InterpreteRisposta.comandoRiuscito(r.testo) != false
        is RisultatoMmi.InviatoSenzaRisposta -> true
        else -> false
    }

    private fun descrivi(r: RisultatoMmi): String = when (r) {
        is RisultatoMmi.Risposta -> "L'operatore ha risposto: ${r.testo.trim()}"
        is RisultatoMmi.Rifiutato -> r.motivo
        is RisultatoMmi.Errore -> r.motivo
        is RisultatoMmi.InviatoSenzaRisposta -> "Codice inviato con ${r.metodo}"
    }

    /** SMS di conferma al numero scelto, se l'opzione è attiva. */
    private suspend fun avvisaSms(testo: String) {
        val pref = impostazioni.preferenzeOra()
        if (!pref.smsCambio || pref.numeroAvvisi.isBlank()) return
        val inviato = sms.invia(pref.numeroAvvisi, testo, pref.simSubId)
        if (inviato) registra(TipoEvento.INFO, Esito.INFO, pref.numeroAvvisi, null, "SMS di conferma inviato")
    }

    private suspend fun attiva(collega: Collega, fine: Long) {
        val pref = impostazioni.preferenzeOra()
        val numero = collega.telefono
        val nome = collega.cognome
        val tentativo = adesso()
        try {
            impostazioni.iniziaTentativo(numero, nome, tentativo)
            notifiche.annullaErrore()
            _occupato.value = "Attivo la deviazione verso $nome…"
            aggiornaNotificheEWidget()
            pianificatore.programmaControlloConferma(tentativo)

            val codice = Telefonia.codiceAttivazione(numero, pref.prefissoInternazionale)
            var r = telefonia.invia(codice, pref.simSubId, riserva = false)
            if (!inviatoBene(r)) {
                _occupato.value = "Primo tentativo non riuscito, riprovo…"
                delay(10_000)
                r = telefonia.invia(codice, pref.simSubId, riserva = true)
            }
            if (!inviatoBene(r)) {
                fallimento(numero, nome, descrivi(r))
                return
            }

            _occupato.value = "Verifico con l'operatore (*#21#)…"
            when (val v = verificaDopoAttivazione(numero, pref.simSubId)) {
                is EsitoVerifica.Confermata -> {
                    impostazioni.impostaStato(StatoDeviazione.CONFERMATA, "Confermata dall'operatore")
                    pianificatore.annullaControlloConferma()
                    registra(TipoEvento.ATTIVAZIONE, Esito.CONFERMATA, numero, nome, "Confermata dall'operatore")
                    notifiche.cambio(collega, fine, confermata = true, dettaglio = null)
                    avvisaSms("Deviazione reperibilità attiva verso ${collega.cognome} (${Nomi.formattaTelefono(numero)}) fino alle ${Formati.ora(fine)}. Confermata dall'operatore.")
                    fermaAllarme(daUtente = false)
                }
                is EsitoVerifica.NonVerificabile -> {
                    val motivo = if (r is RisultatoMmi.InviatoSenzaRisposta)
                        "codice inviato con il metodo di riserva, la risposta appare solo a schermo" else v.motivo
                    impostazioni.impostaStato(StatoDeviazione.IN_ATTESA, motivo)
                    registra(TipoEvento.ATTIVAZIONE, Esito.NON_VERIFICATA, numero, nome, "Inviata, conferma non arrivata: $motivo")
                    notifiche.cambio(collega, fine, confermata = false, dettaglio = motivo)
                    avvisaSms("Deviazione reperibilità verso ${collega.cognome} (${Nomi.formattaTelefono(numero)}) fino alle ${Formati.ora(fine)}. Inviata, in attesa di conferma dell'operatore.")
                }
                is EsitoVerifica.Diversa -> fallimento(numero, nome, v.motivo)
            }
        } finally {
            _occupato.value = null
            aggiornaNotificheEWidget()
        }
    }

    private suspend fun verificaDopoAttivazione(numero: String, sim: Int): EsitoVerifica {
        var ultimo: StatoOperatore? = null
        var testo: String? = null
        for (attesa in listOf(3_000L, 20_000L, 30_000L)) {
            delay(attesa)
            val r = telefonia.invia(Telefonia.VERIFICA, sim, riserva = false)
            if (r !is RisultatoMmi.Risposta) {
                impostazioni.registraVerifica(adesso(), descrivi(r), null)
                return EsitoVerifica.NonVerificabile(descrivi(r))
            }
            testo = r.testo
            val so = InterpreteRisposta.stato(r.testo)
            ultimo = so
            if (so is StatoOperatore.Attiva && so.numeri.any { Nomi.stessoNumero(it, numero) }) {
                impostazioni.registraVerifica(adesso(), r.testo, true)
                return EsitoVerifica.Confermata(r.testo)
            }
        }
        impostazioni.registraVerifica(adesso(), testo, false)
        return when (val u = ultimo) {
            is StatoOperatore.Attiva ->
                EsitoVerifica.Diversa("L'operatore indica la deviazione verso ${u.numeri.joinToString { Nomi.formattaTelefono(it) }} invece di ${Nomi.formattaTelefono(numero)}")
            StatoOperatore.NonAttiva -> EsitoVerifica.Diversa("L'operatore indica che la deviazione NON è attiva")
            else -> EsitoVerifica.NonVerificabile("risposta dell'operatore non chiara: ${testo.orEmpty().trim()}")
        }
    }

    private suspend fun fallimento(numero: String?, nome: String?, motivo: String) {
        impostazioni.impostaStato(StatoDeviazione.ERRORE, motivo)
        avvisaSms("ATTENZIONE: deviazione reperibilità verso ${nome ?: "il turno"} NON riuscita. $motivo")
        pianificatore.annullaControlloConferma()
        registra(TipoEvento.ATTIVAZIONE, Esito.ERRORE, numero, nome, motivo)
        notifiche.errore(motivo)
        impostazioni.impostaAllarmeAttivo(true)
        pianificatore.suonaAllarmeSubito()
        aggiornaNotificheEWidget()
    }

    /** Scatta 2 minuti dopo un tentativo: se manca la conferma dell'operatore fa partire l'allarme. */
    suspend fun controlloConferma(tentativo: Long) {
        val st = impostazioni.statoOra()
        val pref = impostazioni.preferenzeOra()
        if (st.tentativo != tentativo) return
        when (st.stato) {
            StatoDeviazione.IN_CORSO, StatoDeviazione.IN_ATTESA -> {
                if (!pref.allarmeSenzaConferma) return
                val motivo = "L'operatore non ha confermato la deviazione verso ${st.nome} entro 2 minuti."
                registra(TipoEvento.ALLARME, Esito.ERRORE, st.numero, st.nome, motivo)
                notifiche.errore(motivo)
                impostazioni.impostaAllarmeAttivo(true)
                AllarmeService.avvia(context, motivo)
            }
            StatoDeviazione.ERRORE -> {
                impostazioni.impostaAllarmeAttivo(true)
                AllarmeService.avvia(context, st.messaggio)
            }
            else -> Unit
        }
    }

    /** L'allarme deve ancora suonare? (non spento dall'utente e deviazione non confermata) */
    suspend fun allarmeNecessario(): Boolean {
        val st = impostazioni.statoOra()
        if (!st.allarmeAttivo) return false
        return st.stato != StatoDeviazione.CONFERMATA && st.stato != StatoDeviazione.DISATTIVATA
    }

    suspend fun motivoAllarme(): String =
        impostazioni.statoOra().messaggio ?: "La deviazione delle chiamate non risulta attiva."

    suspend fun fermaAllarme(daUtente: Boolean) {
        val eraAttivo = impostazioni.statoOra().allarmeAttivo
        pianificatore.annullaRipetizioneAllarme()
        impostazioni.impostaAllarmeAttivo(false)
        AllarmeService.spegniSeAttivo(context)
        if (daUtente && eraAttivo) registra(TipoEvento.ALLARME, Esito.INFO, null, null, "Allarme spento dall'utente")
    }

    /**
     * Verifica silenziosa durante l'allarme (senza popup): true se l'operatore conferma il numero atteso.
     */
    suspend fun verificaSilenziosa(): Boolean {
        val pref = impostazioni.preferenzeOra()
        val st = impostazioni.statoOra()
        val atteso = situazione().numeroAtteso ?: st.numero ?: return false
        val r = telefonia.invia(Telefonia.VERIFICA, pref.simSubId, riserva = false)
        if (r !is RisultatoMmi.Risposta) return false
        val so = InterpreteRisposta.stato(r.testo)
        val ok = so is StatoOperatore.Attiva && so.numeri.any { Nomi.stessoNumero(it, atteso) }
        impostazioni.registraVerifica(adesso(), r.testo, ok)
        if (ok) {
            val collega = situazione().collega
            impostazioni.iniziaTentativo(atteso, collega?.cognome ?: st.nome.orEmpty(), st.tentativo.coerceAtLeast(1))
            impostazioni.impostaStato(StatoDeviazione.CONFERMATA, "Confermata dall'operatore")
            registra(TipoEvento.VERIFICA, Esito.CONFERMATA, atteso, collega?.cognome ?: st.nome, "Confermata durante l'allarme")
            notifiche.annullaErrore()
            aggiornaNotificheEWidget()
        }
        return ok
    }

    /** Pulsante "Verifica stato": *#21# e confronto con il numero atteso. */
    suspend fun verifica() {
        val pref = impostazioni.preferenzeOra()
        try {
            _occupato.value = "Verifico lo stato con l'operatore (*#21#)…"
            val s = situazione()
            val st = s.stato
            val atteso = if (pref.automatico && s.collega != null) s.numeroAtteso else st.numero
            val nomeAtteso = if (pref.automatico && s.collega != null) s.collega.cognome else st.nome
            when (val r = telefonia.invia(Telefonia.VERIFICA, pref.simSubId, riserva = true)) {
                is RisultatoMmi.Risposta -> {
                    val so = InterpreteRisposta.stato(r.testo)
                    when {
                        so is StatoOperatore.Attiva && atteso != null && so.numeri.any { Nomi.stessoNumero(it, atteso) } -> {
                            impostazioni.registraVerifica(adesso(), r.testo, true)
                            impostazioni.iniziaTentativo(atteso, nomeAtteso.orEmpty(), adesso())
                            impostazioni.impostaStato(StatoDeviazione.CONFERMATA, "Confermata dall'operatore")
                            registra(TipoEvento.VERIFICA, Esito.CONFERMATA, atteso, nomeAtteso, r.testo.trim())
                            notifiche.annullaErrore()
                            fermaAllarme(daUtente = false)
                        }
                        so is StatoOperatore.Attiva -> {
                            val numeri = so.numeri.joinToString { Nomi.formattaTelefono(it) }
                            impostazioni.registraVerifica(adesso(), r.testo, atteso == null)
                            val motivo = if (atteso != null) "Attiva verso $numeri invece di ${Nomi.formattaTelefono(atteso)} ($nomeAtteso)"
                            else "Attiva verso $numeri"
                            if (atteso != null) impostazioni.impostaStato(StatoDeviazione.ERRORE, motivo)
                            registra(TipoEvento.VERIFICA, if (atteso != null) Esito.ERRORE else Esito.INFO, so.numeri.first(), null, motivo)
                        }
                        so is StatoOperatore.NonAttiva -> {
                            impostazioni.registraVerifica(adesso(), r.testo, atteso == null)
                            if (atteso != null && pref.automatico) {
                                impostazioni.impostaStato(StatoDeviazione.ERRORE, "L'operatore indica che la deviazione NON è attiva")
                                registra(TipoEvento.VERIFICA, Esito.ERRORE, atteso, nomeAtteso, "Deviazione non attiva secondo l'operatore")
                            } else {
                                impostazioni.impostaDisattivata("Confermato dall'operatore: nessuna deviazione", adesso())
                                registra(TipoEvento.VERIFICA, Esito.INFO, null, null, "Nessuna deviazione attiva")
                            }
                        }
                        else -> {
                            impostazioni.registraVerifica(adesso(), r.testo, null)
                            registra(TipoEvento.VERIFICA, Esito.NON_VERIFICATA, atteso, nomeAtteso, "Risposta non chiara: ${r.testo.trim()}")
                        }
                    }
                }
                is RisultatoMmi.InviatoSenzaRisposta -> {
                    val testo = "Risposta mostrata da Android in un popup: l'app non può leggerla"
                    impostazioni.registraVerifica(adesso(), testo, null)
                    registra(TipoEvento.VERIFICA, Esito.NON_VERIFICATA, atteso, nomeAtteso, testo)
                }
                else -> {
                    impostazioni.registraVerifica(adesso(), descrivi(r), null)
                    registra(TipoEvento.VERIFICA, Esito.ERRORE, atteso, nomeAtteso, descrivi(r))
                }
            }
        } finally {
            _occupato.value = null
            aggiornaNotificheEWidget()
        }
    }

    /** Pulsante "Disattiva deviazione": ##21# e sospensione dell'automatismo. */
    suspend fun disattiva() {
        val pref = impostazioni.preferenzeOra()
        val st = impostazioni.statoOra()
        try {
            impostazioni.impostaAutomatico(false)
            pianificatore.annullaControlloConferma()
            fermaAllarme(daUtente = false)
            _occupato.value = "Disattivo la deviazione (##21#)…"
            val r = telefonia.invia(Telefonia.DISATTIVA, pref.simSubId, riserva = true)
            if (!inviatoBene(r)) {
                val motivo = "Disattivazione non riuscita: ${descrivi(r)}"
                impostazioni.impostaStato(StatoDeviazione.ERRORE, motivo)
                registra(TipoEvento.DISATTIVAZIONE, Esito.ERRORE, st.numero, st.nome, motivo)
                notifiche.errore(motivo, conRiprova = false)
                return
            }
            if (r is RisultatoMmi.InviatoSenzaRisposta) {
                impostazioni.impostaDisattivata("Codice inviato, conferma a schermo", adesso())
                registra(TipoEvento.DISATTIVAZIONE, Esito.NON_VERIFICATA, st.numero, st.nome, descrivi(r))
                return
            }
            _occupato.value = "Verifico la disattivazione (*#21#)…"
            delay(3_000)
            val v = telefonia.invia(Telefonia.VERIFICA, pref.simSubId, riserva = false)
            val so = (v as? RisultatoMmi.Risposta)?.let { InterpreteRisposta.stato(it.testo) }
            when (so) {
                StatoOperatore.NonAttiva -> {
                    impostazioni.registraVerifica(adesso(), (v as RisultatoMmi.Risposta).testo, true)
                    impostazioni.impostaDisattivata("Confermata dall'operatore", adesso())
                    registra(TipoEvento.DISATTIVAZIONE, Esito.CONFERMATA, st.numero, st.nome, "Disattivazione confermata dall'operatore")
                }
                is StatoOperatore.Attiva -> {
                    val motivo = "La deviazione risulta ancora attiva verso ${so.numeri.joinToString { Nomi.formattaTelefono(it) }}"
                    impostazioni.registraVerifica(adesso(), (v as RisultatoMmi.Risposta).testo, false)
                    impostazioni.impostaStato(StatoDeviazione.ERRORE, motivo)
                    registra(TipoEvento.DISATTIVAZIONE, Esito.ERRORE, st.numero, st.nome, motivo)
                    notifiche.errore(motivo, conRiprova = false)
                }
                else -> {
                    impostazioni.impostaDisattivata("Disattivata (conferma non arrivata)", adesso())
                    registra(TipoEvento.DISATTIVAZIONE, Esito.NON_VERIFICATA, st.numero, st.nome, descrivi(r))
                }
            }
        } finally {
            _occupato.value = null
            aggiornaNotificheEWidget()
        }
    }

    /** Esito della "Prova deviazione" con una chiamata da un altro telefono. */
    suspend fun esitoProvaChiamata(riuscita: Boolean) {
        val s = situazione()
        val numero = s.stato.numero ?: s.numeroAtteso
        val nome = s.stato.nome ?: s.collega?.cognome
        if (riuscita) {
            registra(TipoEvento.PROVA_CHIAMATA, Esito.RIUSCITA, numero, nome, "La chiamata di prova è arrivata al collega")
            if (s.stato.stato == StatoDeviazione.IN_ATTESA || s.stato.stato == StatoDeviazione.ERRORE) {
                impostazioni.impostaStato(StatoDeviazione.CONFERMATA, "Confermata dalla chiamata di prova")
                notifiche.annullaErrore()
                fermaAllarme(daUtente = false)
            }
        } else {
            registra(TipoEvento.PROVA_CHIAMATA, Esito.NON_RIUSCITA, numero, nome, "La chiamata di prova NON è arrivata al collega")
            impostazioni.impostaStato(StatoDeviazione.ERRORE, "La chiamata di prova non è arrivata al collega")
        }
        aggiornaNotificheEWidget()
    }
}
