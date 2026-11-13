package it.reperibilita.deviazione.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class Voce { TELEFONO, NOTIFICHE, SCHERMO_INTERO, ALLARMI_ESATTI, BATTERIA }

data class Controllo(val voce: Voce, val titolo: String, val descrizione: String, val ok: Boolean)

/** Controlli di configurazione: permessi e impostazioni di sistema necessari al funzionamento automatico. */
object Configurazione {

    fun concesso(context: Context, permesso: String) =
        ContextCompat.checkSelfPermission(context, permesso) == PackageManager.PERMISSION_GRANTED

    fun permessiDaChiedere(context: Context): Array<String> = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filterNot { concesso(context, it) }.toTypedArray()

    fun batteriaOk(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun schermoInteroOk(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun allarmiEsattiOk(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun notificheOk(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || concesso(context, Manifest.permission.POST_NOTIFICATIONS)) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun controlli(context: Context): List<Controllo> = listOf(
        Controllo(
            Voce.TELEFONO, "Permesso Telefono",
            "Serve per inviare i codici di deviazione e scegliere la SIM.",
            concesso(context, Manifest.permission.CALL_PHONE) && concesso(context, Manifest.permission.READ_PHONE_STATE),
        ),
        Controllo(Voce.NOTIFICHE, "Notifiche", "Per avvisarti a ogni cambio turno e in caso di errore.", notificheOk(context)),
        Controllo(
            Voce.SCHERMO_INTERO, "Allarme a tutto schermo",
            "Per mostrare l'allarme anche a telefono bloccato.", schermoInteroOk(context),
        ),
        Controllo(Voce.ALLARMI_ESATTI, "Sveglie e promemoria", "Per cambiare la deviazione all'ora esatta.", allarmiEsattiOk(context)),
        Controllo(
            Voce.BATTERIA, "Nessuna ottimizzazione batteria",
            "Così Android non blocca l'app quando il telefono è inattivo.", batteriaOk(context),
        ),
    )

    private fun apri(context: Context, vararg intenti: Intent): Boolean {
        for (i in intenti) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private fun dettagliApp(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    @SuppressLint("BatteryLife")
    fun chiediBatteria(context: Context) = apri(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        dettagliApp(context),
    )

    fun apriSchermoIntero(context: Context) = apri(
        context,
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
        dettagliApp(context),
    )

    fun apriAllarmiEsatti(context: Context) = apri(
        context,
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        dettagliApp(context),
    )

    fun apriNotifiche(context: Context) = apri(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        dettagliApp(context),
    )

    fun apriDettagliApp(context: Context) = apri(context, dettagliApp(context))

    /** Pagina batteria di Samsung (Assistenza dispositivo), con alternative se il percorso cambia. */
    fun apriBatteriaSamsung(context: Context) = apri(
        context,
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        dettagliApp(context),
    )

    fun eSamsung(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}

/** Un contatore che cambia a ogni ritorno sulla schermata: serve a ricalcolare i controlli. */
@Composable
fun ricontrollaAlRitorno(): Int {
    var giro by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        giro++
        onPauseOrDispose { }
    }
    return giro
}
