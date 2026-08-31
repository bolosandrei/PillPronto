package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment

// Limita e un plafon de bun-simt (evita nume absurd de lungi in UI/notificari), nu o regula de business.
private const val MAX_NAME_LENGTH = 200

/**
 * Guard defensiv la nivel de domeniu — ruleaza indiferent de caller (UI, teste, import viitor).
 * UI-ul (AddTreatmentViewModel) valideaza aceleasi reguli mai devreme, pentru feedback imediat;
 * asta ramane linia a doua de aparare, nu prima sursa de mesaje de eroare pentru utilizator.
 */
internal fun validateTreatment(treatment: Treatment) {
    require(treatment.medicationName.isNotBlank()) { "Numele medicamentului este obligatoriu" }
    require(treatment.medicationName.length <= MAX_NAME_LENGTH) {
        "Numele medicamentului este prea lung (max $MAX_NAME_LENGTH caractere)"
    }
    // Tratamentele "la nevoie" (PRN) nu au orar fix; celelalte trebuie sa aiba cel putin o ora.
    if (!treatment.asNeeded) {
        require(treatment.times.isNotEmpty()) { "Cel putin o ora de administrare este necesara" }
    }
    if (treatment.endDate != null) {
        require(!treatment.endDate.isBefore(treatment.startDate)) {
            "Data de final nu poate fi înainte de data de start"
        }
    }
}
