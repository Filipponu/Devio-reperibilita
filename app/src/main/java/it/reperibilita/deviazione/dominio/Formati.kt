package it.reperibilita.deviazione.dominio

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formattazione di date e durate in italiano, fuso Europe/Rome. */
object Formati {
    private val it = Locale.ITALIAN
    private val ora = DateTimeFormatter.ofPattern("HH:mm", it)
    private val giorno = DateTimeFormatter.ofPattern("EEE d MMM", it)
    private val giornoLungo = DateTimeFormatter.ofPattern("EEEE d MMMM", it)
    private val dataOra = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm", it)
    private val dataBreveOra = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", it)

    fun zdt(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZONA_ROMA)

    fun ora(millis: Long): String = ora.format(zdt(millis))
    fun giorno(millis: Long): String = giorno.format(zdt(millis)).replaceFirstChar { it.titlecase(this.it) }
    fun giornoLungo(millis: Long): String = giornoLungo.format(zdt(millis)).replaceFirstChar { it.titlecase(this.it) }
    fun dataOra(millis: Long): String = dataOra.format(zdt(millis))
    fun giornoOra(millis: Long): String = dataBreveOra.format(zdt(millis)).replaceFirstChar { it.titlecase(this.it) }

    /** "oggi alle 20:00", "domani alle 08:00" o "ven 13 nov alle 08:00". */
    fun quando(millis: Long, adesso: Long = System.currentTimeMillis()): String {
        val giorni = ChronoUnit.DAYS.between(zdt(adesso).toLocalDate(), zdt(millis).toLocalDate())
        val prefisso = when (giorni) {
            0L -> "oggi"
            1L -> "domani"
            -1L -> "ieri"
            else -> giorno.format(zdt(millis))
        }
        return "$prefisso alle ${ora(millis)}"
    }

    /** "2 h 15 min", "45 min", "3 g 4 h". */
    fun durata(millis: Long): String {
        val minuti = (millis / 60_000).coerceAtLeast(0)
        val g = minuti / (60 * 24)
        val h = (minuti / 60) % 24
        val m = minuti % 60
        return when {
            g > 0 -> if (h > 0) "$g g $h h" else "$g g"
            h > 0 -> if (m > 0) "$h h $m min" else "$h h"
            else -> "$m min"
        }
    }
}
