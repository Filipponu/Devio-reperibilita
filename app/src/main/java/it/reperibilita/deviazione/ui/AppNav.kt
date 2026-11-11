package it.reperibilita.deviazione.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Scheda(val rotta: String, val etichetta: String, val icona: ImageVector)

private val schede = listOf(
    Scheda("stato", "Stato", Icons.Rounded.PhoneForwarded),
    Scheda("turni", "Turni", Icons.Rounded.CalendarMonth),
    Scheda("rubrica", "Rubrica", Icons.Rounded.Contacts),
    Scheda("registro", "Registro", Icons.Rounded.History),
    Scheda("impostazioni", "Opzioni", Icons.Rounded.Settings),
)

private val TIPI_EXCEL = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

@Composable
fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val situazione by vm.situazione.collectAsState()
    val turni by vm.turni.collectAsState()
    val colleghi by vm.colleghi.collectAsState()
    val registro by vm.registro.collectAsState()
    val preferenze by vm.preferenze.collectAsState()
    val occupato by vm.occupato.collectAsState()
    val importazione by vm.importazione.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var prova by remember { mutableStateOf(false) }

    val sceltaFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.caricaExcel(uri)
    }
    val permessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val importa = { runCatching { sceltaFile.launch(TIPI_EXCEL) }.onFailure { vm.messaggio("Nessuna app per scegliere i file") } }

    LaunchedEffect(Unit) {
        val mancanti = Configurazione.permessiDaChiedere(context)
        if (mancanti.isNotEmpty()) permessi.launch(mancanti)
    }
    LaunchedEffect(Unit) { vm.messaggi.collect { snackbar.showSnackbar(it) } }

    val voce by nav.currentBackStackEntryAsState()
    val rotta = voce?.destination?.route
    LaunchedEffect(importazione) {
        if (importazione !is StatoImport.Nessuno && nav.currentDestination?.route != "importa") nav.navigate("importa")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (rotta != "importa") {
                NavigationBar {
                    schede.forEach { s ->
                        NavigationBarItem(
                            selected = rotta == s.rotta,
                            onClick = {
                                nav.navigate(s.rotta) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(s.icona, null) },
                            label = { Text(s.etichetta, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val basso = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(
            nav, startDestination = "stato",
            modifier = Modifier.padding(basso).consumeWindowInsets(basso),
        ) {
            composable("stato") {
                SchermataHome(
                    situazione = situazione,
                    occupato = occupato,
                    turni = turni,
                    colleghi = colleghi,
                    registro = registro,
                    onApplica = vm::applicaOra,
                    onVerifica = vm::verifica,
                    onDisattiva = vm::disattiva,
                    onImporta = { importa() },
                    onProva = { prova = true },
                    onTuttiITurni = { nav.navigate("turni") { launchSingleTop = true } },
                    onTuttoIlRegistro = { nav.navigate("registro") { launchSingleTop = true } },
                    onImpostazioni = { nav.navigate("impostazioni") { launchSingleTop = true } },
                )
            }
            composable("turni") {
                SchermataTurni(turni, colleghi, situazione?.ora ?: System.currentTimeMillis(), onImporta = { importa() })
            }
            composable("rubrica") {
                SchermataRubrica(colleghi, onSalva = { vm.salvaCollega(it) }, onElimina = { vm.eliminaCollega(it) })
            }
            composable("registro") { SchermataRegistro(registro) }
            composable("impostazioni") {
                SchermataImpostazioni(
                    preferenze = preferenze,
                    simDisponibili = vm::simDisponibili,
                    onNumero = { vm.impostaNumeroReperibilita(it) },
                    onSim = { vm.impostaSim(it) },
                    onAutomatico = { vm.impostaAutomatico(it) },
                    onAllarmeSenzaConferma = { vm.impostaAllarmeSenzaConferma(it) },
                    onPrefisso = { vm.impostaPrefisso(it) },
                    onProvaDeviazione = { prova = true },
                    onProvaAllarme = vm::provaAllarme,
                )
            }
            composable("importa") {
                SchermataImport(
                    stato = importazione,
                    colleghi = colleghi,
                    onAnnulla = {
                        vm.annullaImport()
                        nav.popBackStack()
                    },
                    onSalva = { vm.salvaImport { nav.popBackStack() } },
                    onAltroFile = { importa() },
                    onAggiungiCollega = { vm.salvaCollega(it) },
                )
            }
        }
    }

    val s = situazione
    if (prova && s != null) {
        DialogProvaDeviazione(
            s, occupato,
            onEsito = { vm.esitoProva(it) },
            onVerifica = vm::verifica,
            onChiudi = { prova = false },
        )
    }
}
