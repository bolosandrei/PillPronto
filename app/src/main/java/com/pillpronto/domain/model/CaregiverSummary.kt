package com.pillpronto.domain.model

/** Un Apartinator legat de un profil de pacient, asa cum il vede Pacientul (Faza 1.5d — ecranul
 * "Gestionează accesul Aparținătorilor"). */
data class CaregiverSummary(
    val userId: String,
    val displayName: String?
)
