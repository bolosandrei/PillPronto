package com.pillpronto.domain.vision.tracking

import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.iou

private const val HIGH_CONFIDENCE_THRESHOLD = 0.5f
private const val ASSOCIATION_IOU_THRESHOLD = 0.3f

/** Tracker multi-obiect stil ByteTrack — asociere in 2 etape (prag inalt intai, prag jos pt.
 * recuperarea track-urilor existente) + filtru Kalman per-track (vezi [BoxTrack]). Fara
 * identificare de clasa (detectorul are o singura clasa, "cutie_medicament") — asocierea
 * foloseste doar IoU intre pozitia PREZISA a fiecarui track si detectiile brute din cadrul curent.
 *
 * Idee ByteTrack (Zhang et al.): o detectie de incredere joasa care nimereste exact unde un track
 * existent a prezis pozitia e probabil obiectul real (ocluzie partiala/blur trecator), nu zgomot —
 * o detectie fals-pozitiva nu are cum sa cada sistematic pe pozitia prezisa a unui track deja
 * confirmat. Recupereaza astfel obiecte reale fara sa foloseasca pragul jos direct ca prag de
 * afisare (doar track-urile CONFIRMATE ajung la UI, vezi [BoxTrack.isConfirmed]).
 *
 * NU e un tracker complet de productie (fara re-ID dupa ocluzie lunga, fara asociere globala
 * optima tip Hungarian — greedy e suficient pt. putine obiecte simultan pe ecran) — scopul e
 * stabilizarea vizuala a conturului/mastii afisate, nu urmarire de precizie stiintifica. Instantiat
 * o singura data per intrare pe ecran (`VisionScanScreen`), NU per-cadru — pastreaza starea
 * track-urilor intre apeluri. */
class MultiObjectTracker {
    private val tracks = mutableListOf<BoxTrack>()
    private var nextTrackId = 0

    /** Apelat o data per cadru analizat, cu detectiile BRUTE (inclusiv sub pragul "vizibil" —
     * vezi pragul jos trimis la `YoloOutputDecoder.decode` din `YoloSegModel.kt`). Intoarce doar
     * track-urile confirmate, cu boxul Kalman-corectat/prezis si `trackId` atasat. */
    fun update(rawDetections: List<Detection>): List<Detection> {
        // Pasul 1: avanseaza toate track-urile (Kalman predict), o singura data per cadru,
        // inainte de orice asociere.
        tracks.forEach { it.predict() }

        val highConfidenceIndices = rawDetections.indices.filter { rawDetections[it].confidence >= HIGH_CONFIDENCE_THRESHOLD }
        val lowConfidenceIndices = rawDetections.indices.filter { rawDetections[it].confidence < HIGH_CONFIDENCE_THRESHOLD }

        val matchedTrackIndices = mutableSetOf<Int>()
        val matchedDetectionIndices = mutableSetOf<Int>()

        // Etapa 1: detectii de prag inalt <-> toate track-urile.
        greedyMatch(tracks.indices.toList(), highConfidenceIndices, rawDetections, matchedTrackIndices, matchedDetectionIndices)

        // Etapa 2: track-urile ramase nepotrivite dupa etapa 1 <-> detectii de prag jos.
        val remainingTrackIndices = tracks.indices.filter { it !in matchedTrackIndices }
        greedyMatch(remainingTrackIndices, lowConfidenceIndices, rawDetections, matchedTrackIndices, matchedDetectionIndices)

        // Track-uri nepotrivite in ambele etape -> rateu (raman "in coasting" pe predictie).
        tracks.indices.filter { it !in matchedTrackIndices }.forEach { tracks[it].markMissed() }

        // Detectii de prag inalt inca nepotrivite dupa etapa 1 -> track nou, tentativ.
        highConfidenceIndices.filter { it !in matchedDetectionIndices }.forEach { index ->
            tracks.add(BoxTrack(nextTrackId++, rawDetections[index]))
        }

        // Curatenie: sterge track-urile care au ratat prea multe cadre consecutive.
        tracks.removeAll { it.shouldDelete() }

        return tracks.filter { it.isConfirmed }.map { it.toDetection() }
    }

    /** Potrivire greedy (nu optim global Hungarian — suficient pt. cateva obiecte simultan):
     * pt. fiecare track din `trackIndices` (in ordine), alege detectia ramasa cu IoU maxim fata de
     * pozitia lui prezisa, daca depaseste [ASSOCIATION_IOU_THRESHOLD]. */
    private fun greedyMatch(
        trackIndices: List<Int>,
        detectionIndices: List<Int>,
        detections: List<Detection>,
        matchedTrackIndices: MutableSet<Int>,
        matchedDetectionIndices: MutableSet<Int>
    ) {
        val availableDetectionIndices = detectionIndices.filter { it !in matchedDetectionIndices }.toMutableList()
        if (availableDetectionIndices.isEmpty()) return

        for (trackIndex in trackIndices) {
            val predictedBox = tracks[trackIndex].currentBox()
            val bestIndex = availableDetectionIndices.maxByOrNull { iou(predictedBox, detections[it].box) } ?: continue
            if (iou(predictedBox, detections[bestIndex].box) < ASSOCIATION_IOU_THRESHOLD) continue

            tracks[trackIndex].update(detections[bestIndex])
            matchedTrackIndices.add(trackIndex)
            matchedDetectionIndices.add(bestIndex)
            availableDetectionIndices.remove(bestIndex)
        }
    }
}
