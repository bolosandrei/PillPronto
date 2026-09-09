package com.pillpronto.domain.model

import java.time.LocalDateTime

/** O doza programata si statusul ei — sursa de date pentru metricile de aderenta. */
data class DoseLog(
    val id: Long = 0L,
    val treatmentId: Long,
    val scheduledAt: LocalDateTime,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: LocalDateTime? = null,
    // Doza logata ad-hoc pentru un tratament "la nevoie" (PRN) — exclusa din calculul PDC/MPR,
    // pentru ca nu exista o "doza programata" fata de care sa se raporteze aderenta.
    val isAsNeeded: Boolean = false,
    // Cantitatea slotului la momentul GENERARII (snapshot, vezi GenerateDosesUseCase) — NU legata
    // live de Treatment (spre deosebire de medicationName/dosage, afisate prin JOIN in
    // DoseItemView — inconsecventa preexistenta). Motiv: DoseLog e sursa de adevar pentru
    // aderenta — o editare ulterioara a cantitatii unui slot nu trebuie sa schimbe istoricul deja
    // logat.
    val cantitate: String = ""
)
