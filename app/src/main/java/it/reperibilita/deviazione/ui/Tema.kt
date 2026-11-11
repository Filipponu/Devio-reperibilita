package it.reperibilita.deviazione.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.reperibilita.deviazione.dominio.Livello

private val ChiaroDiRiserva = lightColorScheme(
    primary = Color(0xFF00677F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EAFF),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF4C626B),
    secondaryContainer = Color(0xFFCFE6F1),
    tertiary = Color(0xFF5A5B7E),
    tertiaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFFF6FAFD),
    surface = Color(0xFFF6FAFD),
)

private val ScuroDiRiserva = darkColorScheme(
    primary = Color(0xFF5DD5FB),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF004E60),
    onPrimaryContainer = Color(0xFFB7EAFF),
    secondary = Color(0xFFB3CAD5),
    secondaryContainer = Color(0xFF344A53),
    tertiary = Color(0xFFC3C3EB),
    tertiaryContainer = Color(0xFF424465),
    background = Color(0xFF0F1417),
    surface = Color(0xFF0F1417),
)

/** Colori fissi dello stato (non dipendono dai colori dinamici, devono restare riconoscibili). */
@Immutable
data class ColoriStato(val inizio: Color, val fine: Color, val testo: Color)

@Immutable
data class Tavolozza(
    val verde: ColoriStato,
    val arancione: ColoriStato,
    val rosso: ColoriStato,
    val grigio: ColoriStato,
    val giorno: Color,
    val notte: Color,
    val h24: Color,
) {
    fun per(livello: Livello) = when (livello) {
        Livello.VERDE -> verde
        Livello.ARANCIONE -> arancione
        Livello.ROSSO -> rosso
        Livello.GRIGIO -> grigio
    }
}

private val TavolozzaChiara = Tavolozza(
    verde = ColoriStato(Color(0xFF13804A), Color(0xFF2DB46B), Color.White),
    arancione = ColoriStato(Color(0xFFD9670B), Color(0xFFF6A019), Color.White),
    rosso = ColoriStato(Color(0xFFB3261E), Color(0xFFE5483B), Color.White),
    grigio = ColoriStato(Color(0xFF5C6670), Color(0xFF85909B), Color.White),
    giorno = Color(0xFFF2A100),
    notte = Color(0xFF4F5BD5),
    h24 = Color(0xFF00897B),
)

private val TavolozzaScura = Tavolozza(
    verde = ColoriStato(Color(0xFF0E5E37), Color(0xFF1E8A52), Color.White),
    arancione = ColoriStato(Color(0xFF9E4A06), Color(0xFFC77812), Color.White),
    rosso = ColoriStato(Color(0xFF8C1D18), Color(0xFFC0392B), Color.White),
    grigio = ColoriStato(Color(0xFF3A4249), Color(0xFF59636C), Color.White),
    giorno = Color(0xFFFFC14D),
    notte = Color(0xFF9AA5FF),
    h24 = Color(0xFF4DD0B9),
)

val LocalTavolozza = staticCompositionLocalOf { TavolozzaChiara }

/** Colori degli avatar: uno stabile per ogni persona. */
val ColoriAvatar = listOf(
    Color(0xFF00897B), Color(0xFF5E35B1), Color(0xFFD81B60), Color(0xFF3949AB),
    Color(0xFFF4511E), Color(0xFF7CB342), Color(0xFF8E24AA), Color(0xFF039BE5),
    Color(0xFF6D4C41), Color(0xFFC0CA33),
)

private val Tipografia = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val Forme = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TemaApp(content: @Composable () -> Unit) {
    val scuro = isSystemInDarkTheme()
    val context = LocalContext.current
    val schema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (scuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scuro -> ScuroDiRiserva
        else -> ChiaroDiRiserva
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTavolozza provides if (scuro) TavolozzaScura else TavolozzaChiara) {
        MaterialTheme(colorScheme = schema, typography = Tipografia, shapes = Forme, content = content)
    }
}
