package it.reperibilita.deviazione

import android.app.Application
import android.content.Context
import it.reperibilita.deviazione.data.AppDatabase
import it.reperibilita.deviazione.data.Impostazioni
import it.reperibilita.deviazione.notifiche.Notifiche
import it.reperibilita.deviazione.servizio.Motore
import it.reperibilita.deviazione.servizio.Pianificatore
import it.reperibilita.deviazione.telefonia.Telefonia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tutte le dipendenze dell'app, create una volta sola. */
class Contenitore(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.crea(context)
    val impostazioni = Impostazioni(context)
    val telefonia = Telefonia(context)
    val notifiche = Notifiche(context)
    val pianificatore = Pianificatore(context, db)
    val motore = Motore(context, db, impostazioni, telefonia, pianificatore, notifiche)
}

class App : Application() {
    lateinit var contenitore: Contenitore
        private set

    override fun onCreate() {
        super.onCreate()
        contenitore = Contenitore(this)
        contenitore.notifiche.creaCanali()
    }
}

val Context.contenitore: Contenitore get() = (applicationContext as App).contenitore
