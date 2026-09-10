package com.pillpronto.ui.navigation

// Etichetele afisate (bottom bar) sunt localizate separat via labelResFor() in PillProntoNavHost,
// nu stocate aici — Route ramane fara dependenta de resurse Android.
sealed class Route(val path: String) {
    data object Today : Route("today")
    data object Treatments : Route("treatments")
    data object Adherence : Route("adherence")
    data object Account : Route("account")
    data object Onboarding : Route("onboarding")
    data object ManageAccess : Route("manage_access")
    data object AssociateGtin : Route("associate_gtin")
    data object ManageProfessionalAccess : Route("manage_professional_access")
    data object MyPatients : Route("my_patients?prefillCode={prefillCode}") {
        const val ARG_PREFILL_CODE = "prefillCode"
        fun create(prefillCode: String? = null) =
            if (prefillCode != null) "my_patients?prefillCode=$prefillCode" else "my_patients"
    }
    data object PatientDetail : Route("patient_detail/{patientProfileId}") {
        const val ARG = "patientProfileId"
        fun create(patientProfileId: String) = "patient_detail/$patientProfileId"
    }
    data object AddEditTreatment : Route("treatment_form?treatmentId={treatmentId}") {
        const val ARG = "treatmentId"
        fun create(id: Long = -1L) = "treatment_form?treatmentId=$id"
    }
    data object TreatmentDetail : Route("treatment_detail/{treatmentId}") {
        const val ARG = "treatmentId"
        fun create(id: Long) = "treatment_detail/$id"
    }

    companion object {
        val bottomBar = listOf(Today, Treatments, Adherence, Account)
    }
}
