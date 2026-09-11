# PillPronto (Android)

Aplicație-suport pentru administrarea medicamentelor — identifică vizual cutiile de pe masă,
urmărește dozele luate/ratate și ajută la respectarea tratamentului. Face parte dintr-o disertație
(UTCN) al cărei scop academic este **analiza îmbunătățirii aderenței la tratament**: aplicația e
*intervenția*, contribuția academică e măsurarea efectului ei asupra aderenței (PDC/MPR + MMAS-8).
De-aceea Faza 1 (aderență) a fost implementată prima — logarea dozelor e sursa de date a studiului
— iar restul funcționalităților (identificare, viziune, recunoaștere) au fost adăugate ulterior,
fiecare validată tehnic înainte de a investi în varianta finală.

## Ce face

- **Tratament**: introducere manuală (nume, dozaj, cantitate, formă, indicație, instrucțiuni),
  orare multiple pe zi (inclusiv cantitate diferită per oră), tratamente „la nevoie" (PRN).
- **Remindere & aderență**: alarme **exacte** per doză + notificări cu acțiuni „Confirmă"/„Omite"
  direct din notificare; istoric per tratament; calcul **PDC** (Proportion of Days Covered) și
  **MPR** (Medication Possession Ratio), cu prag standard ≥0.80.
- **Identificare medicament**: căutare în **Nomenclatorul ANMDMR** (32.500+ produse, cu fallback
  fuzzy — tolerant la greșeli de tastare) + scanare **cod de bare GS1 DataMatrix** (obligatoriu UE
  pe cutii Rx, conform Reg. Delegat (UE) 2016/161) — citește GTIN, lot, serie și **data
  expirării**, cu alerte automate la apropierea acesteia.
- **Scanare vizuală de ansamblu** (experimental): camera detectează și segmentează cutiile de pe
  masă (nu cutie-cu-cutie), cu contur real (nu doar dreptunghi) pe fiecare obiect recunoscut.
- **Recunoaștere pe embeddings** (experimental, în construcție): userul poate „înrola" un
  medicament nou filmând cutia din mai multe unghiuri și confirmând manual produsul corect din
  Nomenclator — fără nicio reantrenare de model, spre deosebire de un clasificator clasic.
- **Conturi & partajare** (opțional — aplicația e complet funcțională fără cont): Pacientul poate
  invita un Aparținător, Medic sau Farmacist să-i vadă tratamentele și aderența (read-only, cu
  notificare la doză ratată pentru Aparținător).

## De ce așa

- **On-device by default**: toate datele de sănătate (tratamente, loguri de doze) rămân local
  (Room), conform GDPR (Art. 9 — categorie specială de date). Sincronizarea cu Supabase e strict
  **opțională**, condiționată de autentificare, și trimite doar statusuri finale de doză ale
  propriului cont — niciodată date brute în cloud fără consimțământ explicit.
- **Identificare în cascadă, nu un singur mecanism**: codul de bare (GS1 DataMatrix) e sursa
  fiabilă (identifică exact GTIN-ul), viziunea localizează cutia pe masă, iar recunoașterea vizuală
  o leagă de un tratament deja introdus. Fiecare mecanism a fost adăugat doar după ce precedentul
  și-a dovedit limitele empiric (ex. OCR simplu a fost încercat, testat live, și **eliminat**
  pentru rată de succes prea scăzută — păstrat doar ca rezultat negativ documentat).
- **Embeddings, nu clasificator închis**: un clasificator ar trebui reantrenat la fiecare
  medicament nou (>10.000 variante posibile într-o singură țară) — modelul de recunoaștere învață
  în schimb un spațiu de similaritate vizuală, iar un medicament nou se adaugă doar prin
  „înrolare" (fotografiere + confirmare umană), fără reantrenare.
- **Validare tehnică înainte de calitate finală**: componentele de viziune/recunoaștere folosesc
  deocamdată modele generice preantrenate (YOLO11n-seg pe clase COCO, MobileNetV3-Small pe
  ImageNet) — nu există încă un dataset propriu de cutii de medicamente RO/UE. Scopul actual e
  validarea pipeline-ului tehnic complet (cameră → model → interfață), nu acuratețea finală de
  recunoaștere — un model antrenat pe date reale va înlocui backbone-urile generice ulterior.

## Arhitectură

Clean Architecture + MVVM:
- `domain/` — modele, interfețe repository, use-case-uri; **fără nicio dependență Android/vendor**
  (testabil pur în JVM, inclusiv logica de viziune/recunoaștere — decodare model, NMS, similaritate).
- `data/` — Room (entități/DAO), implementări de repository, remindere (AlarmManager/WorkManager),
  client Supabase, modele ML (LiteRT, MediaPipe).
- `ui/` — Jetpack Compose (ecrane + ViewModels), navigare pe tab-uri (Azi, Tratamente, Aderență,
  Cont) + ecrane secundare (scanare, asociere coduri, înrolare).
- `core/di/` — module Hilt.

**Stack**: Kotlin, Jetpack Compose (Material 3), Hilt, Room, Coroutines/Flow, WorkManager +
AlarmManager, CameraX, **LiteRT** (inferență YOLO11n-seg on-device) + **MediaPipe Tasks**
(embeddings on-device, cu accelerare GPU), ML Kit / Play Services (coduri de bare), Supabase
Kotlin SDK (Auth + Postgrest, opțional).

## Stadiu actual

- ✅ **Faza 0 — Setup**: Gradle KTS + version catalog, Compose, Hilt, Navigation, temă.
- ✅ **Faza 1 — MVP aderență**: tratamente, remindere exacte, confirmare/omitere doză, calcul
  PDC/MPR, ecran „Azi" + ecran de aderență.
- ✅ **Faza 1.5 — Conturi & roluri**: autentificare (email/parolă + Google), sincronizare
  Room↔Supabase, fluxuri Aparținător/Medic/Farmacist (invitație pe cod/QR, acces read-only).
- ✅ **Faza 2 — Identificare**: import Nomenclator ANMDMR (căutare fuzzy), scanare GS1 DataMatrix
  + catalog partajat de mapări GTIN→produs, dată expirare + alerte. *(OCR ca metodă de identificare
  a fost încercat și eliminat — rată de succes insuficientă.)*
- ✅ **Faza 3 (parțial) — Viziune**: feed live de cameră (CameraX), detecție + segmentare
  multi-obiect on-device (YOLO11n-seg via LiteRT, GPU-accelerat — ~5x mai rapid decât CPU),
  contur real (mască, nu doar dreptunghi) pe fiecare obiect detectat.
- 🚧 **Faza 4 (parțial) — Recunoaștere & enrollment**: pipeline de embeddings validat tehnic
  (MediaPipe, model generic) + flux funcțional de înrolare (captură multi-unghi → confirmare
  manuală din Nomenclator → salvare locală). Recunoașterea efectivă la scanare (nearest-neighbor)
  și colorarea conturului după statusul dozei rămân de implementat.
- ⏳ **Următoarele faze**: tracking + persistență spațială (ByteTrack + ancore ARCore), chatbot
  RAG pentru întrebări despre tratament + verificare interacțiuni medicamentoase, hardening GDPR
  și instrumentare pentru studiul pilot de aderență.

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
   Pentru un proiect Supabase nou, rulează migrările din `supabase/migrations/` din Dashboard →
   SQL Editor, **în ordine numerică**, înainte de primul login din aplicație.
2. Deschide folderul în Android Studio — sincronizează Gradle și configurează wrapper-ul (sau
   rulează `gradle wrapper` dacă ai Gradle instalat global).
3. **Modelele ML** (`.tflite`) pentru scanarea vizuală/recunoaștere sunt deja comise în
   `app/src/main/assets/` — nu necesită niciun pas suplimentar pentru a rula. Scriptul
   `scripts/export-yolo-seg-model.py` documentează cum a fost exportat modelul de detecție, pentru
   cazul în care vrei să-l re-exporți (cere Linux/macOS sau Google Colab, nu rulează pe Windows).
4. Rulează pe emulator/dispozitiv. Pe Android 12+ acordă permisiunea de „alarme exacte" și
   notificările, din setările aplicației; scanarea vizuală cere și permisiunea de cameră.

```bash
./gradlew assembleDebug              # build APK debug
./gradlew testDebugUnitTest          # teste unitare (domain + logica pura de viziune/recunoastere)
./gradlew connectedDebugAndroidTest  # teste instrumentate (necesita device/emulator conectat)
```

## Roadmap (rest de implementat)

- Faza 4c — recunoaștere efectivă la scanare (nearest-neighbor pe galeria locală) + colorare
  contur după statusul dozei (verde/portocaliu/roșu/gri).
- Faza 5 — tracking multi-obiect (ByteTrack) + netezire + ancore ARCore pentru persistență
  spațială la scanare progresivă.
- Faza 6 — chatbot RAG peste tratamentul activ + prospecte, cu verificare de interacțiuni
  medicamentoase.
- Faza 7 — hardening GDPR (consimțământ, ștergere), battery optimization, instrumentare pentru
  studiul pilot de aderență.
