# PillPronto (Android)

Aplicație-suport pentru administrarea medicamentelor. Miezul academic al proiectului este
**analiza îmbunătățirii aderenței la tratament** — de aceea Faza 1 (aderență) e implementată prima.

## Stadiu actual (Faza 0 + Faza 1)

- **Faza 0 — Setup:** Gradle KTS + version catalog, Jetpack Compose, Hilt (DI), Navigation, temă.
- **Faza 1 — MVP aderență:**
  - Introducere manuală tratament (nume, dozaj, ore).
  - Persistență locală (Room, offline-first pentru GDPR).
  - Remindere ca alarme **exacte** (AlarmManager) + notificări, reprogramare la boot.
  - Confirmare/omitere doză (loguri = sursă de date pentru aderență).
  - Calcul **PDC** și **MPR** + ecran de aderență (prag PDC ≥ 0.80).

## Arhitectură

Clean Architecture + MVVM:
- `domain/` — modele, interfețe repository, use-case-uri (fără dependențe Android).
- `data/` — Room (entities/DAO), repository impl, remindere (AlarmManager).
- `ui/` — Compose (ecrane + ViewModels), navigare.
- `core/di/` — module Hilt.

## Build

Necesită **Android Studio** (JDK 17, Android SDK 35). 

1. Deschide folderul în Android Studio — va sincroniza Gradle și va configura wrapper-ul
   (sau rulează `gradle wrapper` dacă ai Gradle instalat global).
2. `minSdk = 26`, `targetSdk = 35`.
3. Rulează pe emulator/dispozitiv. Pe Android 12+ acordă permisiunea de „alarme exacte"
   și notificările, din setările aplicației.

## Roadmap (următoarele faze)

- Faza 2 — Identificare: import Nomenclator ANMDMR, scanare DataMatrix/barcode + OCR (ML Kit).
- Faza 3 — Viziune: detecție + segmentare (YOLO-seg via LiteRT/ONNX), contururi colorate.
- Faza 4 — Recunoaștere & enrollment few-shot (metric learning).
- Faza 5 — Tracking + AR overlay.
- Faza 6 — Chatbot RAG + verificare interacțiuni.
- Faza 7 — GDPR, battery optimization, teste, studiu pilot de aderență.

> Notă: culorile de status ale dozei (verde/portocaliu/roșu/gri) sunt deja definite în temă
> (`core/ui/theme/Theme.kt`) pentru a fi reutilizate la conturul AR din Faza 3–4.
