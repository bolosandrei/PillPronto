package com.pillpronto.data.notification

import com.pillpronto.domain.model.Treatment
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryAlertCheckerTest {

    private val today = LocalDate.of(2026, 1, 1)

    private fun treatment(id: Long, expiryDate: LocalDate?) = Treatment(
        id = id,
        medicationName = "Algocalmin",
        dosage = "500 mg",
        startDate = today,
        expiryDate = expiryDate
    )

    @Test
    fun `tratament fara expiryDate e ignorat`() {
        val alerts = ExpiryAlertChecker.computeAlerts(listOf(treatment(1, null)), today, emptySet())

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `expirare peste prag nu genereaza alerta`() {
        val t = treatment(1, today.plusDays(30))

        val alerts = ExpiryAlertChecker.computeAlerts(listOf(t), today, emptySet())

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `expirare sub prag dar in viitor genereaza NEAR_EXPIRY`() {
        val t = treatment(1, today.plusDays(10))

        val alerts = ExpiryAlertChecker.computeAlerts(listOf(t), today, emptySet())

        assertEquals(1, alerts.size)
        assertEquals(ExpiryStage.NEAR_EXPIRY, alerts.first().stage)
    }

    @Test
    fun `expirare in trecut genereaza EXPIRED`() {
        val t = treatment(1, today.minusDays(5))

        val alerts = ExpiryAlertChecker.computeAlerts(listOf(t), today, emptySet())

        assertEquals(1, alerts.size)
        assertEquals(ExpiryStage.EXPIRED, alerts.first().stage)
    }

    @Test
    fun `cheie deja notificata nu se regenereaza`() {
        val t = treatment(1, today.plusDays(10))
        val dedupKey = "1:${today.plusDays(10)}:NEAR_EXPIRY"

        val alerts = ExpiryAlertChecker.computeAlerts(listOf(t), today, setOf(dedupKey))

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `rescanare cu expirare diferita genereaza alerta noua chiar daca vechea expirare a fost notificata`() {
        val oldExpiry = today.plusDays(10)
        val newExpiry = today.plusDays(12) // cutie noua, tot in fereastra "aproape expirat", dar alta data
        val t = treatment(1, newExpiry)
        val oldDedupKey = "1:$oldExpiry:NEAR_EXPIRY"

        val alerts = ExpiryAlertChecker.computeAlerts(listOf(t), today, setOf(oldDedupKey))

        // Cheia veche (legata de expirarea anterioara) nu blocheaza alerta pt. noua expirare —
        // chei distincte, fara mecanism explicit de reset.
        assertEquals(1, alerts.size)
        assertEquals(newExpiry, alerts.first().expiryDate)
    }
}
