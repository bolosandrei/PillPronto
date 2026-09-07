# PillPronto (Android)

Aplicație-suport pentru administrarea medicamentelor. Miezul academic al proiectului este
**analiza îmbunătățirii aderenței la tratament** — de aceea Faza 1 (aderență) a fost implementată
prima, offline-first, fără dependență de cont.

## Stadiu actual

- **Faza 0 — Setup:** Gradle KTS + version catalog, Jetpack Compose, Hilt (DI), Navigation, temă.
- **Faza 1 — MVP aderență (complet):**
  - Introducere/editare tratament (nume, dozaj, ore, interval, tratamente „la nevoie" — PRN).
  - Persistență locală (Room, offline-first pentru GDPR) — funcționează integral fără cont.
  - Remindere ca alarme **exacte** (AlarmManager) + notificări cu acțiuni, reprogramare la boot.
  - Confirmare/omitere doză (loguri = sursă de date pentru aderență); istoric per tratament.
  - Calcul **PDC** și **MPR** + ecran de aderență (prag PDC ≥ 0.80).
  - Ecranul „Azi": fereastră de zile + selector lună/an (picker nativ pentru salt pe orice dată).
  - Localizare completă în `strings.xml` (scaffolding pentru switch RO/EN viitor).
- **Faza 1.5a+1.5b — Conturi & roluri (parțial):**
  - Backend Supabase (Postgres + Auth + Row-Level-Security, regiune UE) — schema pregătită pentru
    Pacient / Aparținător / Medic / Farmacist, cu partajare pe bază de invitație explicită.
  - Autentificare email/parolă + onboarding de rol, funcțională end-to-end. **Cont opțional** —
    aplicația rămâne complet utilizabilă fără login.
  - Rămân: Google Sign-In, sincronizare Room↔Supabase, fluxurile efective de Aparținător/Medic/
    Farmacist. Detalii complete: [`docs/user-management-plan.md`](docs/user-management-plan.md).

## Arhitectură

Clean Architecture + MVVM:
- `domain/` — modele, interfețe repository, use-case-uri (fără dependențe Android/vendor).
- `data/` — Room (entities/DAO), repository impl, remindere (AlarmManager), client Supabase.
- `ui/` — Compose (ecrane + ViewModels), navigare (4 tab-uri: Azi, Tratamente, Aderență, Cont).
- `core/di/` — module Hilt.

**Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, Coroutines/Flow, WorkManager +
AlarmManager, Supabase Kotlin SDK (Auth + Postgrest).

## Build & rulare

Necesită **Android Studio** (JDK 17, Android SDK — `compileSdk 36`, `minSdk 26`, `targetSdk 35`).

1. **Cont Supabase (obligatoriu pentru build)** — proiectul citește credențialele din
   `local.properties` (fișier local, gitignored) la compilare; fără el, build-ul eșuează
   explicit cu o eroare de Gradle. Adaugă în `local.properties`:
   ```properties
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_PUBLISHABLE_KEY=<publishable key din Project Settings -> API>
   ```
   Foloseste cheia **Publishable** (nu Secret) — e sigură pentru client, protejată de RLS.
   Pentru un proiect Supabase nou, rulează `supabase/migrations/0001_init_schema.sql` și
   `0002_rls_policies.sql` din Dashboard → SQL Editor înainte de primul login din aplicație.
2. Deschide folderul în Android Studio — sincronizează Gradle și configurează wrapper-ul (sau
   rulează `gradle wrapper` dacă ai Gradle instalat global).
3. Rulează pe emulator/dispozitiv. Pe Android 12+ acordă permisiunea de „alarme exacte" și
   notificările, din setările aplicației.

```bash
./gradlew assembleDebug          # build APK debug
./gradlew testDebugUnitTest      # teste unitare
./gradlew connectedDebugAndroidTest  # teste instrumentate (necesită device/emulator conectat)
```

## Roadmap (următoarele faze)

- Faza 1.5c-1.5g — sync Room↔Supabase, fluxuri Aparținător/Medic/Farmacist, audit & consimțământ.
- Faza 2 — Identificare: import Nomenclator ANMDMR, scanare DataMatrix/barcode + OCR (ML Kit).
- Faza 3 — Viziune: detecție + segmentare (YOLO-seg via LiteRT/ONNX), contururi colorate.
- Faza 4 — Recunoaștere & enrollment few-shot (metric learning).
- Faza 5 — Tracking + AR overlay.
- Faza 6 — Chatbot RAG + verificare interacțiuni.
- Faza 7 — GDPR, battery optimization, teste, studiu pilot de aderență.

> Notă: culorile de status ale dozei (verde/portocaliu/roșu/gri) sunt deja definite în temă
> (`core/ui/theme/Theme.kt`) pentru a fi reutilizate la conturul AR din Faza 3–4.
