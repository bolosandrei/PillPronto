package com.pillpronto.domain.vision.tracking

/** Filtru Kalman 1D cu model de viteza constanta (stare = [pozitie, viteza]) — un pas = un cadru
 * analizat (dt=1 implicit, timpul e masurat in cadre, nu in secunde reale). Folosit ca element de
 * baza pt. [BoxTrack] (4 instante independente: cx, cy, width, height) — vezi acel fisier pt.
 * motivul simplificarii bloc-diagonale fata de un model Kalman 7D corelat (formularea SORT
 * clasica). Covarianta P e pastrata ca 3 valori (`p00`, `p01`, `p11`) — matricea 2x2 e simetrica
 * prin constructie (proprietate standard a filtrului Kalman), `p10 == p01` mereu, deci nu are
 * rost sa fie stocat separat. */
class KalmanFilter1D(
    initialPosition: Float,
    private val processNoise: Float = 1e-2f,
    private val measurementNoise: Float = 1e-1f
) {
    var position: Float = initialPosition
        private set
    var velocity: Float = 0f
        private set

    private var p00 = 1f
    private var p01 = 0f
    private var p11 = 1f

    /** Avanseaza starea cu un pas de timp (F = [[1,1],[0,1]]) si creste incertitudinea cu
     * zgomotul de proces. Trebuie apelat o singura data per cadru, inainte de [update]. */
    fun predict(): Float {
        position += velocity
        // velocity ramane neschimbata (model de viteza constanta)

        val newP00 = p00 + 2 * p01 + p11 + processNoise
        val newP01 = p01 + p11
        val newP11 = p11 + processNoise
        p00 = newP00
        p01 = newP01
        p11 = newP11

        return position
    }

    /** Corecteaza starea cu o masuratoare reala (ecuatiile standard de update Kalman, cu
     * H = [1,0] — masuram direct pozitia, nu viteza). */
    fun update(measurement: Float) {
        val innovation = measurement - position
        val s = p00 + measurementNoise
        val k0 = p00 / s
        val k1 = p01 / s

        position += k0 * innovation
        velocity += k1 * innovation

        val newP00 = (1 - k0) * p00
        val newP01 = (1 - k0) * p01
        val newP11 = p11 - k1 * p01
        p00 = newP00
        p01 = newP01
        p11 = newP11
    }
}
