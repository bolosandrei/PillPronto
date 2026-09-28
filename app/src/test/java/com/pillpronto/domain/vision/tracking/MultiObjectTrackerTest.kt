package com.pillpronto.domain.vision.tracking

import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.RectF01
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiObjectTrackerTest {

    private fun detection(box: RectF01, confidence: Float) = Detection(
        classId = 0,
        label = "cutie_medicament",
        confidence = confidence,
        box = box
    )

    private val boxA = RectF01(cx = 0.3f, cy = 0.3f, width = 0.1f, height = 0.1f)
    private val boxB = RectF01(cx = 0.8f, cy = 0.8f, width = 0.1f, height = 0.1f)

    /** Trimite aceeasi detectie de suficiente ori cat sa treaca de pragul de confirmare
     * ([DEFAULT_MIN_HITS_TO_CONFIRM]) — testele nu hardcodeaza numarul, ca sa nu se dezactualizeze
     * la o viitoare ajustare a pragului (ca cea de la 2 la 5, 2026-09-28). */
    private fun confirmTrack(tracker: MultiObjectTracker, box: RectF01, confidence: Float = 0.9f): List<Detection> {
        var result = emptyList<Detection>()
        repeat(DEFAULT_MIN_HITS_TO_CONFIRM) {
            result = tracker.update(listOf(detection(box, confidence)))
        }
        return result
    }

    @Test
    fun `o singura detectie in primul cadru nu apare inca (track tentativ)`() {
        val tracker = MultiObjectTracker()
        val result = tracker.update(listOf(detection(boxA, confidence = 0.9f)))
        assertEquals(0, result.size)
    }

    @Test
    fun `dupa DEFAULT_MIN_HITS_TO_CONFIRM potriviri consecutive, track-ul e confirmat si apare cu trackId`() {
        val tracker = MultiObjectTracker()
        val result = confirmTrack(tracker, boxA)

        assertEquals(1, result.size)
        assertEquals(0, result[0].trackId)
    }

    @Test
    fun `doua obiecte separate primesc trackId-uri distincte`() {
        val tracker = MultiObjectTracker()
        var result = emptyList<Detection>()
        repeat(DEFAULT_MIN_HITS_TO_CONFIRM) {
            result = tracker.update(listOf(detection(boxA, confidence = 0.9f), detection(boxB, confidence = 0.9f)))
        }

        assertEquals(2, result.size)
        assertNotEquals(result[0].trackId, result[1].trackId)
    }

    @Test
    fun `o detectie de prag jos recupereaza un track confirmat, nu il lasa sa rateze`() {
        val tracker = MultiObjectTracker()
        confirmTrack(tracker, boxA)

        // Cadrul urmator: aceeasi pozitie, dar incredere SUB pragul inalt (0.5) — trebuie
        // recuperata in etapa 2, nu tratata ca rateu.
        val result = tracker.update(listOf(detection(boxA, confidence = 0.2f)))

        assertEquals(1, result.size)
        assertEquals(0, result[0].trackId)
    }

    @Test
    fun `un track e sters dupa prea multe cadre consecutive fara nicio detectie`() {
        val tracker = MultiObjectTracker()
        confirmTrack(tracker, boxA)

        repeat(DEFAULT_MAX_MISSED_FRAMES) {
            val result = tracker.update(emptyList())
            // Cat timp nu a depasit pragul, track-ul confirmat tot apare (coasting pe predictie).
            assertEquals(1, result.size)
        }

        // Un cadru in plus, peste prag -> track-ul trebuie sters.
        val result = tracker.update(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `o detectie noua langa un track existent nu creeaza un al doilea track pentru acelasi obiect`() {
        val tracker = MultiObjectTracker()
        confirmTrack(tracker, boxA)

        // Usor deplasata, dar suprapunere IoU mare fata de pozitia prezisa — tot acelasi track.
        val slightlyMoved = boxA.copy(cx = boxA.cx + 0.01f)
        val result = tracker.update(listOf(detection(slightlyMoved, confidence = 0.9f)))

        assertEquals(1, result.size)
        assertTrue("Ramane acelasi trackId, nu unul nou", result[0].trackId == 0)
    }
}
