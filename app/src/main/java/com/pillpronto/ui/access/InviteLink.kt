package com.pillpronto.ui.access

/**
 * Sursa unica de adevar pt. schema/host-ul deep link-ului de invitatie (Faza 1.5d) — folosita
 * atat la construire (ManageAccessScreen: text distribuit + continut QR) cat si la parsare
 * (MainActivity.handleIntent, vezi AndroidManifest.xml pt. intent-filter-ul corespunzator).
 *
 * Schema proprie (`pillpronto://`), nu domeniu real + Android App Links — functioneaza doar daca
 * aplicatia e deja instalata pe telefonul care deschide link-ul (oricum necesar ca sa poata
 * revendica un cod), fara nicio infrastructura de hosting/verificare.
 *
 * Parsare pe `String`, nu pe `android.net.Uri`: `Uri` e o clasa Android stub in testele JVM
 * (fara Robolectric, `isReturnDefaultValues = true` face `Uri.parse(...)` sa intoarca mereu
 * campuri null) — string-parsing simplu ramane testabil direct, MainActivity trece
 * `intent.data?.toString()`.
 */
private const val SCHEME = "pillpronto"
private const val HOST = "invite"
private const val CODE_PARAM = "code"
private const val PREFIX = "$SCHEME://$HOST"

fun buildInviteUri(code: String): String = "$PREFIX?$CODE_PARAM=$code"

/** Null daca `uriString` nu corespunde formatului asteptat sau nu contine un cod. */
fun extractInviteCode(uriString: String?): String? {
    if (uriString == null || !uriString.startsWith(PREFIX)) return null
    val query = uriString.substringAfter('?', missingDelimiterValue = "")
    return query.split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == CODE_PARAM }
        ?.get(1)
        ?.takeIf { it.isNotBlank() }
}
