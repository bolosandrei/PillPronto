package com.pillpronto.ui.navigation

// Etichetele afisate (bottom bar) sunt localizate separat via labelResFor() in PillProntoNavHost,
// nu stocate aici — Route ramane fara dependenta de resurse Android.
sealed class Route(val path: String) {
    data object Today : Route("today")
    data object Treatments : Route("treatments")
    data object Adherence : Route("adherence")
    data object Account : Route("account")
    data object Onboarding : Route("onboarding")
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
