package com.pillpronto.ui.navigation

sealed class Route(val path: String, val label: String) {
    data object Today : Route("today", "Azi")
    data object Treatments : Route("treatments", "Tratamente")
    data object Adherence : Route("adherence", "Aderență")
    data object AddEditTreatment : Route("treatment_form?treatmentId={treatmentId}", "Tratament") {
        const val ARG = "treatmentId"
        fun create(id: Long = -1L) = "treatment_form?treatmentId=$id"
    }

    companion object {
        val bottomBar = listOf(Today, Treatments, Adherence)
    }
}
