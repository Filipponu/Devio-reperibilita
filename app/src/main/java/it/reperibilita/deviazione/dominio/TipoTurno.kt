package it.reperibilita.deviazione.dominio

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

val ZONA_ROMA: ZoneId = ZoneId.of("Europe/Rome")

enum class TipoTurno(val etichetta: String, val orario: String) {
    GIORNO("Giorno", "08:00 – 20:00"),
    NOTTE("Notte", "20:00 – 08:00"),
    H24("24 ore", "08:00 – 08:00");

    /** Inizio e fine del turno che parte nel giorno [data], con fuso Europe/Rome (cambio d'ora incluso). */
    fun intervallo(data: LocalDate): Pair<ZonedDateTime, ZonedDateTime> {
        val otto = LocalTime.of(8, 0)
        val venti = LocalTime.of(20, 0)
        return when (this) {
            GIORNO -> ZonedDateTime.of(data, otto, ZONA_ROMA) to ZonedDateTime.of(data, venti, ZONA_ROMA)
            NOTTE -> ZonedDateTime.of(data, venti, ZONA_ROMA) to ZonedDateTime.of(data.plusDays(1), otto, ZONA_ROMA)
            H24 -> ZonedDateTime.of(data, otto, ZONA_ROMA) to ZonedDateTime.of(data.plusDays(1), otto, ZONA_ROMA)
        }
    }

    companion object {
        /** Codice normalizzato: maiuscolo, senza spazi ("24 H" → "24H"). */
        fun normalizzaCodice(codice: String): String =
            codice.uppercase(Locale.ROOT).filterNot { it.isWhitespace() || it == '.' }

        /**
         * G = giorno, N = notte, 24H = 24 ore. La "D" finale (GD, ND, 24HD) indica i festivi:
         * gli orari sono gli stessi. Tutti gli altri valori vengono ignorati.
         */
        fun daCodice(codice: String): TipoTurno? = when (normalizzaCodice(codice)) {
            "G", "GD" -> GIORNO
            "N", "ND" -> NOTTE
            "24H", "24HD" -> H24
            else -> null
        }
    }
}
