package com.pillpronto.domain.util

/** Distanta Levenshtein (numarul minim de inserari/stergeri/inlocuiri ca sa transformi `a` in
 * `b`) — folosita ca fallback "fuzzy" la cautarea in Nomenclator
 * (`data/repository/NomenclatureRepositoryImpl.kt`), cand potrivirea de prefix exact (FTS4)
 * esueaza din cauza unei singure litere citite gresit prin OCR (ex. "Algocalnin" in loc de
 * "Algocalmin" — FTS4 cere potrivire EXACTA de la inceputul token-ului, o singura litera diferita
 * la mijlocul cuvantului rupe complet potrivirea). Implementare clasica cu programare dinamica,
 * 2 randuri (nu matrice completa) — suficient pt. nume de medicamente (cateva zeci de caractere). */
fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var previousRow = IntArray(b.length + 1) { it }
    var currentRow = IntArray(b.length + 1)

    for (i in 1..a.length) {
        currentRow[0] = i
        for (j in 1..b.length) {
            val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
            currentRow[j] = minOf(
                currentRow[j - 1] + 1,           // inserare
                previousRow[j] + 1,               // stergere
                previousRow[j - 1] + substitutionCost // inlocuire (sau potrivire, cost 0)
            )
        }
        val swap = previousRow
        previousRow = currentRow
        currentRow = swap
    }
    return previousRow[b.length]
}
