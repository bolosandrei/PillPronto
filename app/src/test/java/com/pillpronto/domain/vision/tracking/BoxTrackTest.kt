package com.pillpronto.domain.vision.tracking

import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.RectF01
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxTrackTest {

    private fun detection(box: RectF01, confidence: Float = 0.9f) = Detection(
        classId = 0,
        label = "cutie_medicament",
        confidence = confidence,
        box = box
    )

    @Test
    fun `un track nou e tentativ, nu confirmat`() {
        val track = BoxTrack(trackId = 1, initialDetection = detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        assertFalse(track.isConfirmed)
    }

    @Test
    fun `devine confirmat dupa DEFAULT_MIN_HITS_TO_CONFIRM potriviri reale`() {
        val track = BoxTrack(trackId = 1, initialDetection = detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        // Constructia initiala numara deja ca prima potrivire - mai raman N-1.
        repeat(DEFAULT_MIN_HITS_TO_CONFIRM - 2) {
            track.predict()
            track.update(detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
            assertFalse("Nu trebuie confirmat inainte de prag", track.isConfirmed)
        }
        track.predict()
        track.update(detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        assertTrue(track.isConfirmed)
    }

    @Test
    fun `markMissed incrementeaza rateurile, update le reseteaza`() {
        val track = BoxTrack(trackId = 1, initialDetection = detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        track.predict()
        track.markMissed()
        track.predict()
        track.markMissed()
        assertEquals(2, track.missedFrames)

        track.predict()
        track.update(detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        assertEquals(0, track.missedFrames)
    }

    @Test
    fun `shouldDelete devine true peste pragul implicit de rateuri`() {
        val track = BoxTrack(trackId = 1, initialDetection = detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        repeat(DEFAULT_MAX_MISSED_FRAMES) {
            track.predict()
            track.markMissed()
        }
        assertFalse("Exact la prag, inca nu trebuie sters", track.shouldDelete())

        track.predict()
        track.markMissed()
        assertTrue("Peste prag, trebuie sters", track.shouldDelete())
    }

    @Test
    fun `toDetection foloseste boxul curent si ataseaza trackId`() {
        val track = BoxTrack(trackId = 42, initialDetection = detection(RectF01(0.5f, 0.5f, 0.2f, 0.2f)))
        val result = track.toDetection()
        assertEquals(42, result.trackId)
        assertEquals(track.currentBox(), result.box)
    }
}
