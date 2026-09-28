package com.pillpronto.domain.vision.tracking

import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.RectF01

const val DEFAULT_MIN_HITS_TO_CONFIRM = 5
const val DEFAULT_MAX_MISSED_FRAMES = 5

/** Un obiect urmarit de-a lungul cadrelor — 4 filtre Kalman 1D independente ([KalmanFilter1D],
 * cate unul pt. `cx`/`cy`/`width`/`height`) + starea de ciclu de viata (tentativ/confirmat,
 * rateuri consecutive) folosita de [MultiObjectTracker] pt. asocierea in 2 etape stil ByteTrack.
 *
 * Ciclu de viata: nou-creat = "tentativ" (nevizibil in UI), devine "confirmat" dupa
 * [DEFAULT_MIN_HITS_TO_CONFIRM] potriviri reale consecutive (evita aparitia unui contur dintr-un
 * singur cadru zgomotos), sters de [MultiObjectTracker] dupa [DEFAULT_MAX_MISSED_FRAMES] cadre
 * consecutive nepotrivite. */
class BoxTrack(val trackId: Int, initialDetection: Detection) {

    private val cxFilter = KalmanFilter1D(initialDetection.box.cx)
    private val cyFilter = KalmanFilter1D(initialDetection.box.cy)
    private val widthFilter = KalmanFilter1D(initialDetection.box.width)
    private val heightFilter = KalmanFilter1D(initialDetection.box.height)

    private var hits = 1

    var missedFrames: Int = 0
        private set

    var lastDetection: Detection = initialDetection
        private set

    val isConfirmed: Boolean get() = hits >= DEFAULT_MIN_HITS_TO_CONFIRM

    fun shouldDelete(maxMissedFrames: Int = DEFAULT_MAX_MISSED_FRAMES): Boolean = missedFrames > maxMissedFrames

    /** Avanseaza starea Kalman cu un pas (un cadru) — TREBUIE apelat o singura data per cadru,
     * inaintea asocierii. [currentBox] dupa acest apel e boxul PREZIS, folosit atat la calculul
     * IoU la potrivire, cat si ca pozitie afisata daca track-ul ramane nepotrivit in acest cadru
     * (coasting pe predictie, nu pe o masuratoare reala). */
    fun predict() {
        cxFilter.predict()
        cyFilter.predict()
        widthFilter.predict()
        heightFilter.predict()
    }

    /** Corecteaza starea Kalman cu o masuratoare reala (detectie potrivita in acest cadru) —
     * reseteaza contorul de rateuri si creste contorul de potriviri (spre confirmare). */
    fun update(detection: Detection) {
        cxFilter.update(detection.box.cx)
        cyFilter.update(detection.box.cy)
        widthFilter.update(detection.box.width)
        heightFilter.update(detection.box.height)
        hits += 1
        missedFrames = 0
        lastDetection = detection
    }

    /** Track nepotrivit in acest cadru — pozitia ramane cea PREZISA de [predict] (coasting). */
    fun markMissed() {
        missedFrames += 1
    }

    fun currentBox(): RectF01 = RectF01(
        cx = cxFilter.position,
        cy = cyFilter.position,
        width = widthFilter.position,
        height = heightFilter.position
    )

    /** Detectia de afisat in UI — eticheta/incredere/masca de la ultima potrivire REALA
     * ([lastDetection], nu se amesteca pixeli de masca intre cadre), boxul e cel curent
     * (Kalman-netezit, posibil doar prezis daca track-ul rateaza cadre recente). */
    fun toDetection(): Detection = lastDetection.copy(box = currentBox(), trackId = trackId)
}
