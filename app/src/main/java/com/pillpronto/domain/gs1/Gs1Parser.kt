package com.pillpronto.domain.gs1

import java.time.LocalDate
import java.time.YearMonth

/** Campurile GS1 mandatate de FMD pe cutiile de medicamente din UE (AI 01/17/10/21) —
 * vezi [Gs1Parser]. Orice camp neprezent in payload ramane null (nu toate cutiile encodeaza
 * toate cele 4, desi in practica GTIN+expirare+lot sunt aproape mereu prezente). */
data class Gs1DecodedData(
    val gtin: String?,
    val batch: String?,
    val expiry: LocalDate?,
    val serial: String?
)

/** Parser minimal pt. payload-ul brut al unui cod GS1 DataMatrix (Barcode.rawValue din ML Kit).
 * Acopera doar cele 4 Application Identifiers mandatate de FMD pe cutiile UE — GTIN (01),
 * expirare (17), lot (10), serial (21) — nu intregul standard GS1 (sute de AI-uri posibile). */
object Gs1Parser {

    // Separator FNC1/GS (ASCII 29) intre campuri de lungime variabila, cand nu sunt ultimul
    // camp din payload — cf. specificatiei GS1 General Specifications.
    private const val GS = '\u001D'

    private const val AI_GTIN = "01"
    private const val AI_EXPIRY = "17"
    private const val AI_BATCH = "10"
    private const val AI_SERIAL = "21"

    private const val GTIN_LENGTH = 14
    private const val EXPIRY_LENGTH = 6
    private const val VARIABLE_FIELD_MAX_LENGTH = 20

    /** null daca payload-ul e gol/invalid sau daca n-a putut fi extras niciun camp cunoscut. */
    fun parse(raw: String): Gs1DecodedData? {
        if (raw.isBlank()) return null

        var i = 0
        var gtin: String? = null
        var batch: String? = null
        var expiry: LocalDate? = null
        var serial: String? = null

        while (i + 2 <= raw.length) {
            val ai = raw.substring(i, i + 2)
            i += 2
            when (ai) {
                AI_GTIN -> {
                    if (i + GTIN_LENGTH > raw.length) break
                    val candidate = raw.substring(i, i + GTIN_LENGTH)
                    if (candidate.all(Char::isDigit)) gtin = candidate
                    i += GTIN_LENGTH
                }
                AI_EXPIRY -> {
                    if (i + EXPIRY_LENGTH > raw.length) break
                    expiry = parseGs1Date(raw.substring(i, i + EXPIRY_LENGTH))
                    i += EXPIRY_LENGTH
                }
                AI_BATCH -> {
                    val (value, next) = readVariableField(raw, i)
                    batch = value
                    i = next
                }
                AI_SERIAL -> {
                    val (value, next) = readVariableField(raw, i)
                    serial = value
                    i = next
                }
                // AI necunoscut: nu-i stim lungimea (fixa sau variabila) -> nu putem continua
                // parsarea in siguranta. Pastram ce am extras deja inaintea lui.
                else -> break
            }
        }

        if (gtin == null && batch == null && expiry == null && serial == null) return null
        return Gs1DecodedData(gtin, batch, expiry, serial)
    }

    /** Camp de lungime variabila: se opreste la separatorul GS sau, daca lipseste (ultimul camp
     * din payload), la lungimea maxima standard. */
    private fun readVariableField(s: String, start: Int): Pair<String, Int> {
        val hardEnd = (start + VARIABLE_FIELD_MAX_LENGTH).coerceAtMost(s.length)
        val gsIndex = s.indexOf(GS, start)
        val end = if (gsIndex in start..hardEnd) gsIndex else hardEnd
        val next = if (end == gsIndex && end < s.length) end + 1 else end
        return s.substring(start, end) to next
    }

    /** YYMMDD -> LocalDate, cu pivotul standard GS1 (>=51 -> secol 1900) si conventia
     * "DD=00 inseamna ultima zi a lunii" (permisa de spec pt. cutii care nu marcheaza ziua exacta). */
    private fun parseGs1Date(yymmdd: String): LocalDate? {
        if (yymmdd.length != EXPIRY_LENGTH || !yymmdd.all(Char::isDigit)) return null
        val yy = yymmdd.substring(0, 2).toInt()
        val mm = yymmdd.substring(2, 4).toInt()
        val dd = yymmdd.substring(4, 6).toInt()
        if (mm !in 1..12) return null
        val year = if (yy >= 51) 1900 + yy else 2000 + yy
        return runCatching {
            if (dd == 0) YearMonth.of(year, mm).atEndOfMonth() else LocalDate.of(year, mm, dd)
        }.getOrNull()
    }
}
