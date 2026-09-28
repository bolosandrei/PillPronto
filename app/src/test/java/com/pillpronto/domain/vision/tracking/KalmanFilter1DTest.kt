package com.pillpronto.domain.vision.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanFilter1DTest {

    @Test
    fun `pozitia initiala e pastrata fara predict sau update`() {
        val filter = KalmanFilter1D(initialPosition = 5f)
        assertEquals(5f, filter.position, 1e-6f)
        assertEquals(0f, filter.velocity, 1e-6f)
    }

    @Test
    fun `predict cu viteza zero nu schimba pozitia`() {
        val filter = KalmanFilter1D(initialPosition = 3f)
        filter.predict()
        assertEquals(3f, filter.position, 1e-6f)
    }

    @Test
    fun `un singur update muta pozitia partial spre masuratoare, nu o suprascrie complet`() {
        // Cu covarianta initiala p00=1 si measurementNoise=0.1 (implicit), k0 = 1/1.1 - primul
        // update se apropie mult de masuratoare (incertitudine initiala mare), dar nu exact 100%.
        val filter = KalmanFilter1D(initialPosition = 0f)
        filter.update(10f)

        val expected = (1f / 1.1f) * 10f // k0 * innovation, innovation = 10 - 0
        assertEquals(expected, filter.position, 1e-3f)
        assertTrue("Pozitia trebuie sa se fi apropiat de masuratoare, dar nu exact la ea", filter.position < 10f)
    }

    @Test
    fun `predict avanseaza pozitia cu viteza curenta`() {
        val filter = KalmanFilter1D(initialPosition = 0f)
        // Cateva cicluri predict+update cu masuratori crescatoare liniar, ca viteza estimata sa
        // devina pozitiva.
        repeat(10) { step ->
            filter.predict()
            filter.update((step + 1) * 2f)
        }
        assertTrue("Viteza estimata ar trebui sa fie pozitiva dupa masuratori crescatoare", filter.velocity > 0f)

        val positionBeforePredict = filter.position
        filter.predict()
        assertTrue(
            "Predict trebuie sa avanseze pozitia in directia vitezei estimate",
            filter.position > positionBeforePredict
        )
    }

    @Test
    fun `converge spre o masuratoare constanta dupa mai multe cicluri`() {
        val filter = KalmanFilter1D(initialPosition = 0f)
        repeat(20) {
            filter.predict()
            filter.update(5f)
        }
        assertEquals(5f, filter.position, 0.1f)
        assertEquals(0f, filter.velocity, 0.1f)
    }
}
