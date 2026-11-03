package com.pillpronto.domain.model

/** Un Apartinator/Medic/Farmacist legat de un profil de pacient, asa cum il vede Pacientul —
 * reutilizat de "Gestionează accesul Aparținătorilor" (1.5d) SI "Acces Medic/Farmacist" (1.5e).
 * `isVerifiedClinician`/`clinicianVerificationType` au sens doar pt. legaturile profesionale —
 * ecranul de Aparținători pur si simplu le ignora. */
data class CaregiverSummary(
    val userId: String,
    val displayName: String?,
    val isVerifiedClinician: Boolean = false,
    val clinicianVerificationType: ClinicianType? = null
)
