# PillPronto — Aplicație Android (memorie de proiect pentru Claude Code CLI)

> Fișier citit automat la începutul fiecărei sesiuni Claude Code CLI din acest folder.
> Există și un `CLAUDE.md` la nivel de teză în folderul-părinte `Disertatie/` (context academic,
> decizii de cercetare, metrici de aderență) — Claude Code CLI îl citește și pe acela.
> Limba de lucru: **română**.

---

## 1. Context pe scurt

Aplicație-suport Android pentru administrarea medicamentelor. Este **intervenția** dintr-o
disertație al cărei scop academic este **analiza îmbunătățirii aderenței la tratament**.
De aceea prioritatea de implementare e **aderența** (logarea dozelor = sursa de date pentru studiu).
Nicio funcționalitate nu se implementează fără o nevoie justificată (sursă științifică / analiză de piață).

## 2. Stack & arhitectură

- **Kotlin + Jetpack Compose**, Material 3, Navigation-Compose.
- **Clean Architecture + MVVM**: `ui/` (Compose + ViewModels) ← `domain/` (modele, use-cases, interfețe repo) ← `data/` (Room, remindere, WorkManager).
- **Hilt** (DI), **Coroutines + Flow**, **Room** (persistență locală, offline-first pentru GDPR).
- **AlarmManager** (alarme exacte) + **WorkManager** (întreținere periodică).
- Viitor: CameraX, ML Kit (barcode+OCR), LiteRT/ONNX Runtime Mobile (viziune), ARCore.

Config: `minSdk = 26`, `targetSdk = 35`, JDK 17, Gradle KTS + version catalog (`gradle/libs.versions.toml`).
`namespace`/`applicationId` = `com.pillpronto`.

## 3. Structura de pachete (`app/src/main/java/com/pillpronto/`)

- `core/di/` — module Hilt (`DatabaseModule`, `RepositoryModule`).
- `core/ui/theme/` — temă + **culori status doză** (`DoseTaken` verde, `DoseDueNow` portocaliu, `DoseMissed` roșu, `DoseUnknown` gri) — reutilizabile la conturul AR (Faza 3–4).
- `core/permissions/` — `Permissions` (verificare/navigare setări alarme exacte).
- `domain/model/` — `Treatment`, `DoseLog`, `DoseStatus`, `DoseItem`, `AdherenceStats`.
- `domain/repository/` — `TreatmentRepository`, `DoseRepository` (interfețe).
- `domain/usecase/` — use-cases (vezi mai jos).
- `data/local/` — Room: `entity/`, `dao/`, `PillProntoDatabase`.
- `data/mapper/` — `Mappers.kt` (entity ↔ domain).
- `data/repository/` — implementări repo.
- `data/reminder/` — `ReminderScheduler`, `ReminderReceiver`, `DoseActionReceiver`, `ReminderCoordinator`, `BootReceiver`.
- `data/work/` — `AdherenceMaintenanceWorker`, `MaintenanceScheduler`.
- `ui/navigation/`, `ui/today/`, `ui/treatments/`, `ui/adherence/`.

## 4. Convenții de cod

- Straturi stricte: `ui` depinde de `domain`; `data` implementează interfețele din `domain`. `domain` NU are dependențe Android.
- Un use-case = o clasă cu `operator fun invoke(...)`, `@Inject constructor`.
- ViewModels `@HiltViewModel`, expun `StateFlow`; UI colectează cu `collectAsStateWithLifecycle`.
- Reminderele sunt legate de **`doseId`** (nu de oră generică). Sursa de adevăr pentru aderență = `DoseLog` din Room.
- Toate datele de sănătate rămân **on-device** (fără cloud pentru loguri) — cerință GDPR.
- **La orice modificare de schemă Room** (câmp nou în `TreatmentEntity`/`DoseLogEntity` etc.) **trebuie incrementat `version` din `@Database`** (`PillProntoDatabase.kt`). `fallbackToDestructiveMigration()` gestionează diferența dintre versiuni (șterge și recreează local — acceptabil în stadiul curent, pre-release), dar Room aruncă `IllegalStateException` la pornire dacă schema s-a schimbat și versiunea a rămas aceeași.
- Teste: JUnit + `kotlinx-coroutines-test` (+ Turbine pentru Flow). Domeniul e testabil pur (java.time).

## 5. Comenzi build & test

```bash
./gradlew assembleDebug          # build APK debug
./gradlew testDebugUnitTest      # teste unitare (PDC/MPR + mappers)
./gradlew installDebug           # instalare pe dispozitiv/emulator conectat
./gradlew lint                   # lint
```

Wrapper-ul Gradle: dacă lipsește `gradlew`, deschide în Android Studio (îl generează) sau rulează `gradle wrapper`.

## 6. Permisiuni & particularități Android

- `POST_NOTIFICATIONS` — cerută la runtime la pornire (Android 13+). Vezi `MainActivity`.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — pe Android 12+ se acordă din setări; ecranul „Azi" arată un **banner** cu buton către setări dacă nu e activă (`Permissions.openExactAlarmSettings`).
- `RECEIVE_BOOT_COMPLETED` — reprogramarea reminderelor după reboot (`BootReceiver`).
- Hilt + WorkManager: `PillProntoApp` implementează `Configuration.Provider`; inițializatorul WorkManager implicit e dezactivat în manifest.
- **De tratat în Faza 7:** battery optimization agresiv pe Xiaomi/Huawei/Samsung (poate întârzia alarmele).

---

## 7. STARE CURENTĂ — ce este IMPLEMENTAT ✅

### Faza 0 — Setup (complet)
Gradle KTS + version catalog, Compose, Hilt, Navigation, temă cu culori de status, structura de pachete.

### Faza 1 — MVP aderență (complet, cu rafinări)
- **Introducere tratament** (manual): nume, dozaj, ore (chips), dată start/end — cu **TimePicker/DatePicker** și validare.
- **Editare & ștergere** tratament (tap pe card → editare; ștergere cu confirmare). Istoricul dozelor luate/ratate se păstrează la editare.
- **Persistență** Room (offline-first): `treatments`, `dose_logs` (FK cascade).
- **Generarea dozelor** din orar (sare peste orele deja trecute).
- **Remindere** ca alarme **exacte** per-doză (`AlarmManager`).
- **Notificări** cu: **tap → deschide app**; butoane **„Confirmă" / „Omite"** (marchează doza direct, via `DoseActionReceiver`, fără a deschide app-ul).
- **WorkManager periodic (6h):** marchează dozele depășite ca `MISSED`, **extinde orizontul** de doze (fereastră rulantă), resincronizează alarmele.
- **Metrici de aderență:** `ComputeAdherenceUseCase` calculează **PDC** (zile acoperite / total) și **MPR** (doze luate / programate); ecran „Aderență" cu prag 0.80.
- **Permisiuni** runtime (notificări) + banner alarme exacte.
- **Teste unitare:** `ComputeAdherenceUseCaseTest` (3 scenarii PDC/MPR), `MappersTest` (round-trip), `FakeDoseRepository`.

Use-cases existente: `AddTreatmentUseCase`, `EditTreatmentUseCase`, `DeleteTreatmentUseCase`, `GetTreatmentUseCase`, `GenerateDosesUseCase`, `ObserveTreatmentsUseCase`, `ObserveTodayDosesUseCase`, `LogDoseUseCase`, `ComputeAdherenceUseCase`, `MarkOverdueDosesUseCase`, `ExtendDoseHorizonUseCase`.

---

## 8. CE URMEAZĂ — TODO

### 8a. Verificare imediată (primul lucru în Claude Code CLI)
> Codul a fost scris în Cowork FĂRĂ a putea rula Gradle (fără Android SDK acolo).
> **Prima acțiune în CLI:** `./gradlew assembleDebug` și `./gradlew testDebugUnitTest` și rezolvă orice eroare de compilare (versiuni, importuri). Logica PDC/MPR a fost validată algoritmic separat, dar build-ul complet nu a fost rulat.

### 8b. Rafinări rămase la Faza 1 (opționale, mici)
- Deep-link din notificare direct în ecranul „Azi" (acum doar deschide app-ul).
- Ecran de detaliu tratament + istoric per medicament; swipe-to-delete în listă.
- Icon launcher propriu (acum folosește iconul implicit); `dataExtractionRules` (Android 12+).
- Localizare completă (extragere stringuri în `strings.xml`).
- Teste instrumentate (Room DAO, Compose UI).

### 8c. Roadmap faze următoare
- **Faza 2 — Identificare:** import Nomenclator ANMDMR (bază locală), scanare **DataMatrix/barcode** (ML Kit) + OCR, legare scanare → tratament. Investigare mapare **GTIN→cod CIM**.
- **Faza 3 — Viziune:** feed CameraX, **YOLO-seg** (LiteRT/ONNX), detecție multi-obiect pe cadru de ansamblu, **contururi gri** (detectat/neidentificat).
- **Faza 4 — Recunoaștere & enrollment:** model de **embeddings** (metric learning), galerie nearest-neighbor, enrollment multi-view + top-k candidați, **colorare contur** după statusul dozei.
- **Faza 5 — Tracking & AR:** ByteTrack + netezire, ancorare dinamică a panoului de info, buton show/hide; ancore ARCore pentru scanare progresivă.
- **Faza 6 — Chatbot RAG + interacțiuni:** RAG peste tratament activ + prospecte, guardrails + disclaimere, verificare interacțiuni medicamentoase.
- **Faza 7 — Hardening & studiu:** GDPR (consimțământ, ștergere), battery optimization, teste, instrumentare pentru studiul pilot de aderență.

---

## 9. Decizii de arhitectură stabile (pe termen lung)

- **Identificare medicament:** cod 2D **GS1 DataMatrix** (GTIN) = sursa fiabilă; viziunea = localizare/AR. Cascadă Etapă 2: **Viziune → OCR → cod 2D**. Fallback: UX ghidat (apropiere cutie). Contur gri când neidentificat.
- **Recunoaștere pe embeddings (metric learning), NU clasificator închis.** Backbone antrenat offline, livrat înghețat; userul înrolează medicamente noi filmând multi-view → embedding + nearest-neighbor, **fără reantrenare**.
- **Chatbot:** RAG obligatoriu peste tratament activ + prospecte; guardrails + disclaimere (informare, nu sfat medical).
- **Metrica academică:** PDC (prag ≥0.80) și/sau MPR; chestionar MMAS-8 (licențiat). Sursa de date = logurile aplicației.

## 10. Workflow de dezvoltare (Claude Code CLI)

- **Plan mode** înainte de fiecare modul/feature nou.
- **Hooks** recomandate: `ktlint`/`detekt` + `testDebugUnitTest` la commit.
- **Git de pe mașina locală** (Windows): repo privat `github.com/bolosandrei/PillPronto`, branch `main`. Commit pe feature + push.
  - Notă: NU rula git din medii care nu-și pot curăța fișierele `.lock` (ex. sandbox Cowork) — lasă `.git/index.lock` blocant.
- Subagenți utili: research (surse), verificare (citări/teste), code-review la PR.
