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
- **Clean Architecture + MVVM**: `ui/` (Compose + ViewModels) ← `domain/` (modele, use-cases, interfețe repo) ← `data/` (Room, remindere, WorkManager, Supabase).
- **Hilt** (DI), **Coroutines + Flow**, **Room** (persistență locală, offline-first pentru GDPR).
- **AlarmManager** (alarme exacte) + **WorkManager** (întreținere periodică, sincronizare, notificări Aparținător).
- **Supabase** (Postgres + Auth + RLS) — strat opțional de conturi/partajare peste Room, vezi Faza 1.5.
- Viitor: CameraX, ML Kit (barcode+OCR), LiteRT/ONNX Runtime Mobile (viziune), ARCore.

Config: `minSdk = 26`, `targetSdk = 35`, `compileSdk = 36` (cerut de `androidx.browser`, tranzitiv
din `auth-kt`), JDK 17, Gradle KTS + version catalog (`gradle/libs.versions.toml`).
`namespace`/`applicationId` = `com.pillpronto`.

## 3. Structura de pachete (`app/src/main/java/com/pillpronto/`)

- `core/di/` — module Hilt (`DatabaseModule`, `RepositoryModule`, `SupabaseModule`).
- `core/ui/theme/` — temă + **culori status doză** (`DoseTaken` verde, `DoseDueNow` portocaliu, `DoseMissed` roșu, `DoseUnknown` gri) — reutilizabile la conturul AR (Faza 3–4).
- `core/ui/components/` — `DatePickerDialogBox`, `BackTopAppBar` (reutilizate pe mai multe ecrane).
- `core/permissions/` — `Permissions` (verificare/navigare setări alarme exacte).
- `domain/model/` — `Treatment`, `DoseLog`, `DoseStatus`, `DoseItem`, `AdherenceStats`, `AccountRole`, `AuthSessionState`, `Profile`, `PatientLink`, `LinkRole`, `LinkedTreatment`, `LinkedDoseLog`, `NomenclatureEntry`.
- `domain/gs1/` — `Gs1Parser`, `Gs1DecodedData` (parser GS1 DataMatrix, Faza 2b-i, fără dependențe Android).
- `domain/repository/` — interfețe (`TreatmentRepository`, `DoseRepository`, `AuthRepository`, `ProfileRepository`, `LinkRepository`, `LinkedPatientDataRepository`, `PatientProfileIdProvider`, `ReminderSync`, `NomenclatureRepository`, `GtinMappingRepository`).
- `domain/usecase/` — use-cases (vezi listele pe fază mai jos).
- `data/local/` — Room: `entity/`, `dao/`, `PillProntoDatabase`, `LocalPatientProfileProvider`.
- `data/mapper/` — `Mappers.kt` (entity ↔ domain).
- `data/repository/` — implementări repo (Room + Supabase).
- `data/reminder/` — `ReminderScheduler`, `ReminderReceiver`, `DoseActionReceiver`, `ReminderCoordinator`, `BootReceiver`.
- `data/sync/` — `SyncManager`, `SyncRemoteDataSource`/`SupabaseSyncDataSource` (Faza 1.5c).
- `data/notification/` — `CaregiverAlertNotifier`, `MissedDoseChecker` (Faza 1.5d).
- `data/work/` — `AdherenceMaintenanceWorker`, `SyncWorker`, `CaregiverAlertWorker`, `MaintenanceScheduler`.
- `ui/navigation/` — `PillProntoNavHost`, `Routes`.
- `ui/today/`, `ui/treatments/`, `ui/adherence/` — Faza 1.
- `ui/account/`, `ui/onboarding/` — Faza 1.5a/b.
- `ui/access/`, `ui/patients/` — Faza 1.5d/e.
- `ui/gtinmapping/` — Faza 2b-i (asociere coduri GTIN).
- `domain/vision/` — `Detection`, `YoloOutputDecoder`, `LetterboxMapper`, `NonMaxSuppression` — Faza 3a-ii (pur Kotlin, testabil).
- `ui/vision/` — `CameraPreview`, `VisionScanScreen`, `YoloSegModel`, `DetectionOverlay` — Faza 3a-i/3a-ii (feed cameră + detecție generică, doar cutii).

## 4. Convenții de cod

- Straturi stricte: `ui` depinde de `domain`; `data` implementează interfețele din `domain`. `domain` NU are dependențe Android. **Excepție documentată, intenționată**: câteva funcții Android-specifice (Credential Manager, scaner QR Play Services) stau direct în `ui/` ca funcții simple, nu trec prin Hilt/domain — cer `Context` de Activity, nu pot fi abstractizate curat fără cost real (vezi `GoogleSignInHelper.kt`, `scanInviteQrCode` în `MyPatientsScreen.kt`).
- Un use-case = o clasă cu `operator fun invoke(...)`, `@Inject constructor`.
- ViewModels `@HiltViewModel`, expun `StateFlow`; UI colectează cu `collectAsStateWithLifecycle`. Pentru evenimente „one-shot" (navigare declanșată de ViewModel, nu derivată din stare comparată în UI) — `Flow` dintr-un `Channel(CONFLATED)`, expus separat de `state`; vezi lecția din secțiunea 7 despre cursa onboarding→bounce-back.
- Reminderele sunt legate de **`doseId`** (nu de oră generică). Sursa de adevăr pentru aderență = `DoseLog` din Room (local) / `dose_logs` (remote, doar status final, vezi Faza 1.5c).
- Toate datele de sănătate rămân **on-device by default** (fără cloud pentru loguri) — cerință GDPR. Sincronizarea cu Supabase (Faza 1.5c+) e strict **opțională**, condiționată de autentificare, și trimite doar date proprii ale Pacientului cu statusuri finale de doză.
- **La orice modificare de schemă Room** (câmp nou într-o entitate etc.) **trebuie incrementat `version` din `@Database`** (`PillProntoDatabase.kt`, actual `version = 7`). `fallbackToDestructiveMigration()` gestionează diferența dintre versiuni (șterge și recreează local — acceptabil pre-release), dar Room aruncă `IllegalStateException` la pornire dacă schema s-a schimbat și versiunea a rămas aceeași.
- **La orice modificare de schemă Postgres**: fișier nou `supabase/migrations/000N_*.sql`, numerotat secvențial, **rulat manual de utilizator** în Supabase Dashboard (SQL Editor), în ordine — Claude Code CLI scrie migrarea, nu o execută.
- Teste: JUnit + `kotlinx-coroutines-test` + **Turbine** (testare `Flow`/evenimente). Domeniul e testabil pur (java.time). Fake-uri de repo/dao în `app/src/test/java/com/pillpronto/util/`.
  Instrumentate (`app/src/androidTest/`): Room DAO pe bază in-memory + un test Compose de fum
  (navigare bottom bar) via `@HiltAndroidTest`; rulează pe emulator/dispozitiv, nu din CLI fără device.
  `testInstrumentationRunner` = `com.pillpronto.HiltTestRunner` (instanțiază `HiltTestApplication`).
- **Stringuri:** toate textele afișate utilizatorului sunt în `res/values/strings.xml` (RO, fără
  calificator de limbă) — nu se hardcodează text în Compose. Erorile de validare din ViewModels
  sunt enum-uri tipizate, nu String — Composable-ul mapează la `stringResource(...)`, ca
  ViewModel-ul să rămână fără dependență de Context Android.
  `AndroidManifest.xml` are `android:localeConfig="@xml/locales_config"` (scaffolding pt. switch
  RO/EN viitor — vezi secțiunea 8a).
- **Credențiale/secrete**: niciodată în cod. `local.properties` (gitignored) → `BuildConfig`, prin
  `requiredLocalProperty(...)` în `app/build.gradle.kts` (aruncă la build dacă lipsește o cheie).

## 5. Comenzi build & test

```bash
./gradlew assembleDebug              # build APK debug
./gradlew testDebugUnitTest          # teste unitare
./gradlew connectedDebugAndroidTest  # teste instrumentate (Room DAO + smoke test Compose) — necesită device/emulator
./gradlew installDebug               # instalare pe dispozitiv/emulator conectat
./gradlew lint                       # lint
```

Wrapper-ul Gradle: dacă lipsește `gradlew`, deschide în Android Studio (îl generează) sau rulează `gradle wrapper`.

Instalare rapidă pe device conectat (fără Android Studio), din Claude Code CLI:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Diagnosticare erori pe device: `adb logcat -d | grep -i <Tag>`. **Notă**: pe telefonul de test al
utilizatorului (MIUI/HyperOS), `Log.d` nu ajunge în logcat by default — pentru logging temporar de
diagnostic pe acest device, folosește `Log.e`/`Log.w`.

## 6. Permisiuni & particularități Android

- `POST_NOTIFICATIONS` — cerută la runtime la pornire (Android 13+). Vezi `MainActivity`.
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — pe Android 12+ se acordă din setări; ecranul „Azi" arată un **banner** cu buton către setări dacă nu e activă (`Permissions.openExactAlarmSettings`).
- `RECEIVE_BOOT_COMPLETED` — reprogramarea reminderelor după reboot (`BootReceiver`).
- Hilt + WorkManager: `PillProntoApp` implementează `Configuration.Provider`; inițializatorul WorkManager implicit e dezactivat în manifest.
- Credential Manager (Google Sign-In) nu cere permisiune de manifest; scanerul QR (Play Services Code Scanner) nici el nu cere `CAMERA` — gestionează propriul UI + permisiune intern.
- **De tratat în Faza 7:** battery optimization agresiv pe Xiaomi/Huawei/Samsung (poate întârzia alarmele).

---

## 7. STARE CURENTĂ — ce este IMPLEMENTAT ✅

### Faza 0 — Setup (complet)
Gradle KTS + version catalog, Compose, Hilt, Navigation, temă cu culori de status, structura de pachete.

### Faza 1 — MVP aderență (complet, inclusiv toate rafinările)
- **Introducere tratament** (manual): nume, dozaj, ore (chips), dată start/end — cu **TimePicker/DatePicker** și validare; tratamente **„la nevoie" (PRN)**, fără orar fix.
- **Editare & ștergere** tratament (tap pe card → editare; ștergere cu confirmare, **swipe-to-delete** în listă). Istoricul dozelor luate/ratate se păstrează la editare.
- **Ecran de detaliu tratament** (`TreatmentDetailScreen`) cu istoric de administrare per medicament.
- **Persistență** Room (offline-first): `treatments`, `dose_logs` (FK cascade).
- **Generarea dozelor** din orar (sare peste orele deja trecute).
- **Ecranul „Azi"**: strip de zile scopat pe **luna afișată** (derivată din `selectedDate`) + rând **lună/an** cu săgeți, eticheta lunii deschide picker-ul nativ M3 (`DatePickerDialogBox`) pentru salt direct pe orice an.
- **Remindere** ca alarme **exacte** per-doză (`AlarmManager`).
- **Notificări** cu: **tap → deschide app și navighează direct pe tab-ul „Azi"** (deep-link prin `MainActivity.onNewIntent` + `EXTRA_OPEN_TODAY`); butoane **„Confirmă" / „Omite"** (marchează doza direct, via `DoseActionReceiver`, fără a deschide app-ul).
- **WorkManager periodic (6h):** marchează dozele depășite ca `MISSED`, **extinde orizontul** de doze (fereastră rulantă), resincronizează alarmele.
- **Metrici de aderență:** `ComputeAdherenceUseCase` calculează **PDC** (zile acoperite / total) și **MPR** (doze luate / programate); ecran „Aderență" cu prag 0.80.
- **Permisiuni** runtime (notificări) + banner alarme exacte.
- **Icon launcher propriu** + `dataExtractionRules` (Android 12+, exclude `pillpronto.db` de la backup cloud).
- **Localizare:** toate stringurile în `strings.xml` (RO); `AddTreatmentError` enum tipizat; `locales_config.xml` — scaffolding pentru switch RO/EN (vezi backlog, secțiunea 8a).
- **Teste unitare:** `ComputeAdherenceUseCaseTest`, `MappersTest`, `FakeDoseRepository`.
- **Teste instrumentate:** `TreatmentDaoTest`, `DoseDaoTest` (Room in-memory, cascade delete), `NavigationSmokeTest` (Compose, navigare bottom bar).

Use-cases: `AddTreatmentUseCase`, `EditTreatmentUseCase`, `DeleteTreatmentUseCase`, `GetTreatmentUseCase`, `GenerateDosesUseCase`, `ObserveTreatmentsUseCase`, `ObserveTodayDosesUseCase`, `ObserveTreatmentHistoryUseCase`, `ObserveActiveAsNeededTreatmentsUseCase`, `LogDoseUseCase`, `LogAsNeededDoseUseCase`, `ComputeAdherenceUseCase`, `MarkOverdueDosesUseCase`, `ExtendDoseHorizonUseCase`.

### Faza 1.5 — Conturi & Roluri (complet, merge-uit pe `main` — PR #1-#8)

Toate sub-fazele (1.5a-1.5e) sunt implementate, merge-uite pe `main` și **testate live pe device
fizic** de utilizator — nu doar unitar. Toate branch-urile de feature au fost șterse (local +
`origin`); repo-ul are un singur branch, `main`. Vezi „Bug-uri semnificative" mai jos pentru
problemele reale găsite prin testarea pe device (RLS, curse de UI) care nu ar fi fost prinse de
code review singur.

**1.5a — Schema Postgres + RLS**
- `patient_profiles.id` = UUID generat client-side (`LocalPatientProfileProvider`, persistat în
  `SharedPreferences` — **un singur UUID per instalare de app**, vezi limitarea cunoscută din
  backlog, secțiunea 8a), devine `patient_profiles.id` în Supabase la onboarding, fără reconciliere.
  `TreatmentEntity`/`DoseLogEntity` au `patientProfileId: String`.
- Schema: `profiles`, `patient_profiles`, `links`, `treatments`, `dose_logs`, `audit_log` — RLS
  activat pe toate.

**1.5b — Autentificare (email/parolă + Google Sign-In)**
- SDK Supabase Kotlin (`core/di/SupabaseModule.kt`, BOM `3.5.0`, module `auth-kt`+`postgrest-kt`).
- Email/parolă: `AuthRepository`/`ProfileRepository` + use-cases (`SignUpUseCase`, `SignInUseCase`,
  `SignOutUseCase`, `ObserveAuthSessionUseCase`, `GetProfileUseCase`, `CompleteOnboardingUseCase`).
  `AuthSessionState` traduce `SessionStatus` din SDK — domeniul nu depinde de vendor.
- **Google Sign-In**: Credential Manager nativ (NU WebView/Custom Tabs OAuth) +
  `supabase.auth.signInWith(IDToken)`. `ui/account/GoogleSignInHelper.kt` (funcție Android-specifică
  în `ui/`, nu trece prin Hilt/domain) generează nonce + hash SHA-256, cere token-ul prin
  `GetGoogleIdOption`, întoarce `GoogleSignInOutcome` tipizat (`Success`/`Cancelled` tăcut/`Failed`).
  `SignInWithGoogleUseCase` + `AuthRepository.signInWithGoogleIdToken` — simetric cu `SignInUseCase`.
  Prerechizite externe (făcute de utilizator, o singură dată): 2 OAuth Client ID Google Cloud
  Console (Web = `serverClientId`, salvat în `GOOGLE_WEB_CLIENT_ID`; Android = package + SHA-1,
  verificat automat de Google, nu intră în cod) + provider Google activat explicit în Supabase
  Dashboard (Client ID+Secret Web, toggle „Enable" **și** „Save" — pas separat de completarea
  câmpurilor). Dependențe: `androidx.credentials`(-play-services-auth),
  `com.google.android.libraries.identity.googleid`.
- Tab „Cont" (al 4-lea, bottom bar) — `AccountScreen` (autentificare/înregistrare + Google) +
  `OnboardingScreen` (alegere rol, o singură dată după primul cont). **Cont opțional** — aplicația
  rămâne 100% funcțională fără login.

**1.5c — Sync layer Room↔Supabase**
- Sincronizare bidirecțională a **propriilor** date ale Pacientului (`treatments`+`dose_logs` cu
  status final) — Room rămâne sursa de adevăr locală, Supabase e strat opțional.
- Dozele PENDING nu se sincronizează (stare de programare locală, nu istoric de aderență) — doar
  `TAKEN`/`MISSED`/`SKIPPED`.
- Outbox pattern: `TreatmentEntity`/`DoseLogEntity` au `remoteId`/`updatedAt`/`dirty`; tabel
  `pending_remote_deletes` pentru ștergeri de propagat. Conflict resolution: last-write-wins pe
  `updatedAt`, un rând local `dirty` nu e niciodată suprascris de un pull.
- `data/sync/SyncManager.kt` — ordine obligatorie: delete-uri → push treatments → push dose_logs →
  pull treatments → pull dose_logs → regenerare doze PENDING → resincronizare alarme.
  `data/work/SyncWorker.kt` — periodic (30 min) + o dată la pornire. No-op dacă userul nu e
  autentificat ca Pacient.
- **Neverificat separat pe device fizic** (Supabase Dashboard) — feature de fundal fără UI propriu;
  posibil exercitat implicit prin testarea 1.5d/1.5e, dar nu confirmat explicit.

**1.5d — Flux Aparținător (viewer)**
- Aparținătorul se leagă de un Pacient **care are deja cont și telefon propriu** (nu „profil
  dependent" — pacient vârstnic fără cont propriu, amânat explicit, cere suport multi-profil local
  în Room, schimbare majoră separată — vezi secțiunea 8b) și îi vede tratamentele + aderența
  read-only, plus notificare la doză ratată (`CaregiverAlertWorker`, 30 min, no-op dacă rolul
  curent != CAREGIVER, deduplicare pe `remoteId` via `MissedDoseChecker`).
- Cod de invitație: 8 caractere alfanumerice (fără 0/O/1/I), generat client-side (`SecureRandom`).
  Distribuire: text prin Android share sheet + **cod QR ca imagine reală atașată**
  (`com.google.zxing:core`, doar generare, via `FileProvider`) — mecanismul „fără tastare"
  funcțional (link-ul cu schemă proprie `pillpronto://invite?code=...` **nu e clickabil în
  WhatsApp** — auto-linkify funcționează doar pe `http(s)://` — păstrat doar ca deep link intern,
  eliminat din textul distribuit). Scanare: `com.google.android.gms:play-services-code-scanner`
  (`GmsBarcodeScanning`) — NU CameraX/ML Kit (acela rămâne pt. Faza 2).
- Fetch date pacient legat: direct din Supabase (`LinkedPatientDataRepository`), niciodată din Room
  local. `AdherenceCalculator` extras din `ComputeAdherenceUseCase` (formulă PDC/MPR pură),
  reutilizat de `GetLinkedPatientAdherenceUseCase`.
- UI: fără tab nou — butoane „Gestionează accesul"/„Pacienții mei" în `AccountScreen`. Ecrane:
  `ManageAccessScreen` (Pacient), `MyPatientsScreen`+`PatientDetailScreen` (Aparținător, read-only).

**1.5e — Flux Medic/Farmacist (read-only)**
- Analog 1.5d — infrastructura de bază (RLS, `MyPatientsScreen`/`PatientDetailScreen`) era deja
  agnostică la rol, reutilizată integral. `CaregiverAlertWorker` rămâne strict pentru CAREGIVER.
- Pacientul alege rolul (Aparținător/Medic/Farmacist) la generarea codului
  (`CreateInviteUseCase(role: LinkRole = CAREGIVER_VIEWER)`); `claim_link` validează că rolul
  contului care revendică se potrivește cu rolul declarat al invitației.
- Ecran separat pentru Pacient (decizie explicită, nu unificat cu Aparținătorii):
  `ManageProfessionalAccessScreen` — selector de rol. Componente comune extrase în
  `ui/access/AccessLinkComponents.kt`.
- Flag „neverificat" (auto-declarare, fără validare CUIM) vizibil pe lista Pacientului **și** pe
  propriul cont al Medicului/Farmacistului.

**Navigare & UX** (cerut de utilizator după testare live pe device)
- Buton de back (`BackTopAppBar`, `core/ui/components/`) pe toate cele 7 ecrane secundare
  (Onboarding, Gestionează accesul ×2, Pacienții mei, Detaliu pacient, Adăugare/editare tratament,
  Detaliu tratament) — parametru `onBack: () -> Unit` legat la `popBackStack()`.
- Fix flash de reîncărcare la revenirea pe tab-uri (Cont, Gestionează accesul ×2, Pacienții mei):
  refresh-urile de fundal (`LifecycleEventEffect(ON_RESUME)`) resetau vizibil starea la spinner
  chiar și cu date deja cunoscute — condiția de randare devine „date cunoscute → le arăt oricum,
  chiar dacă refresh-ul rulează tăcut pe fundal", spinner doar la primul fetch real.

**Migrări Supabase** (`supabase/migrations/`, rulate manual de utilizator în Supabase Dashboard SQL
Editor, **strict în ordine**):

| Migrare | Ce face |
|---|---|
| `0001_init_schema.sql` | Schema inițială (1.5a) |
| `0002_rls_policies.sql` | Politici RLS inițiale (1.5a) |
| `0003_links_open_invite.sql` | `claim_link` SECURITY DEFINER + trigger guard pe update |
| `0004_fix_links_rls_recursion.sql` | Fix recursivitate RLS infinită (`links`↔`patient_profiles`) |
| `0005_profiles_visible_to_linked_grantee.sql` | Pacientul vede numele Aparținătorului legat |
| `0006_fix_links_reinvite_constraint.sql` | Permite reinvitarea unui Aparținător revocat anterior |
| `0007_professional_invite_role_check.sql` | Validare rol la revendicarea codului (1.5e) |

**Neconfirmat exhaustiv de utilizator** că toate cele 7 migrări sunt rulate — testarea funcțională
live a acoperit implicit fluxurile testate, dar nu verificat explicit migrare-cu-migrare.

**Teste unitare noi în Faza 1.5** (pe lângă cele din Faza 1): `AccountViewModelTest`,
`OnboardingViewModelTest`, `SyncManagerTest`, `AdherenceCalculatorTest`, `MissedDoseCheckerTest`,
`GetLinkedPatientAdherenceUseCaseTest`, `ManageAccessViewModelTest`, `MyPatientsViewModelTest`,
`PatientDetailViewModelTest`, `InviteLinkTest`, `ManageProfessionalAccessViewModelTest`. Fake-uri
noi în `util/`: `FakeAuthRepository`, `FakeProfileRepository`, `FakeTreatmentDao`, `FakeDoseDao`,
`FakePendingRemoteDeleteDao`, `FakeSyncRemoteDataSource`, `FakeReminderSync`,
`FakePatientProfileIdProvider`, `FakeLinkRepository`, `FakeLinkedPatientDataRepository`,
`MainDispatcherRule`. **Turbine** (testare `Flow`/evenimente) folosit prima dată la
`AccountViewModelTest`.

### Bug-uri semnificative găsite prin testare pe device (lecții de reținut)

Toate găsite prin **testare live pe device fizic**, nu prin code review — pattern de reținut pentru
acest proiect: recursivitatea RLS și cursele de UI din Compose nu se prind static.

1. **Recursivitate RLS infinită** (`links`↔`patient_profiles`, migrarea 0004): politici care se
   interoghează reciproc prin subquery-uri corelate declanșează reevaluare RLS la nesfârșit. Fix
   standard Postgres/Supabase: funcții `SECURITY DEFINER` care ocolesc RLS intern, în loc de
   subquery-uri corelate directe în politici.
2. **Constrângere unică prea largă** (migrarea 0006): `unique(patient_profile_id, grantee_user_id)`
   se aplica și rândurilor `revoked`, blocând reinvitarea unui Aparținător revocat anterior. Fix:
   index unic parțial (`where status <> 'revoked'`).
3. **Cursa onboarding→bounce-back** — bug găsit de 3 ori (1.5b, 1.5d/1.5e, Google Sign-In), primele
   două „fix-uri" fiind doar reordonări de efecte Compose, insuficiente. Cauza reală (găsită cu
   logcat live pe device): `state` din `AccountScreen` (`collectAsStateWithLifecycle()`) e un
   `State` Compose **derivat**, cu un hop de coroutine în urma lui `vm.state.value` (actualizat
   sincron) — un `LaunchedEffect` care compară stare Compose derivată imediat după un refresh
   sincron poate citi în continuare valoarea veche. **Fix definitiv**:
   `AccountViewModel.needsOnboardingEvents: Flow<Unit>` (`Channel(CONFLATED)`), emis direct din
   ViewModel, în aceeași coroutină care actualizează starea — UI doar colectează evenimentul, fără
   nicio comparație de stare derivată. **Lecție generală**: când un `LaunchedEffect` reacționează la
   stare Compose derivată dintr-un `StateFlow` al unui ViewModel actualizat sincron în altă parte,
   există risc de decalaj — preferabil un eveniment explicit emis de ViewModel.
4. **Link-uri cu schemă proprie (`pillpronto://...`) nu sunt clickabile în WhatsApp** —
   auto-linkify funcționează doar pe `http(s)://`. Pivot la cod QR ca imagine atașată (nu doar
   codat în text) ca mecanism principal „fără tastare".
5. **`provider_disabled` la Google Sign-In**: activarea providerului Google în Supabase Dashboard
   cere explicit toggle „Enable" **și** „Save" — separat de completarea câmpurilor Client
   ID/Secret; ratat prima dată, eroare clară în logcat a confirmat cauza.
6. **`LocalPatientProfileProvider` — un singur UUID local per instalare, nu per cont Supabase** —
   coliziune RLS (`42501`) la testarea cu mai multe conturi Google noi pe același telefon.
   Nereparat, notat în backlog (secțiunea 8a).
7. **Notă de debugging device**: pe telefonul de test al utilizatorului (MIUI/HyperOS), `Log.d` nu
   ajunge în logcat by default — doar `Log.e`/`Log.w`.

### Faza 2a — Identificare: import Nomenclator + căutare/asociere (implementat — 2026-09-09)

✅ **Mergeuit pe `main`** (PR #10, 2026-09-10) — testat unitar+instrumentat, testat live pe device
(notificări confirmate OK peste noapte), migrările `0008`/`0009` rulate manual de utilizator.

- **Import Nomenclator ANMDMR**: descărcat + parsat direct (`scripts/convert-nomenclator.ps1` —
  xlsx e zip, `sharedStrings.xml`+`sheet1.xml` parsate ca XML .NET, fără nicio librărie externă)
  → `app/src/main/assets/nomenclator.tsv.gz` (32.517 rânduri, 20 coloane). **Găsire confirmată, nu
  doar risc teoretic**: Nomenclatorul public NU are coloană GTIN — identifică prin `Cod CIM`, nu
  cod de bare. Decizie: identificarea se face prin **potrivire text** (nume/DCI/concentrație), nu
  lookup GTIN direct; scanarea de coduri (Faza 2b) va construi propria mapare GTIN→CIM local, pe
  măsură ce userii confirmă potriviri.
  - **Gotcha AGP găsit la testare**: un asset `.gz` e **decomprimat automat** de Android Gradle
    Plugin la împachetare, cu extensia scoasă (`nomenclator.tsv.gz` → `nomenclator.tsv` în APK) —
    codul citește direct, fără `GZIPInputStream` (altfel `FileNotFoundException`, cauza reală
    ascunsă până la inspectarea conținutului APK-ului cu `unzip -l`).
  - `NomenclatureDatabase` (Room, separată de `PillProntoDatabase` — date de referință statice, nu
    de sănătate) + tabel normal + **FTS4** (`remove_diacritics=2`) — import batched într-o singură
    tranzacție (`withTransaction`, altfel un import întrerupt la mijloc rămâne "aparent complet"
    pentru totdeauna). `@Insert(onConflict = IGNORE)` — **date reale ANMDMR conțin Cod CIM
    duplicat** (calitate discutabilă a sursei, găsit la primul import pe device:
    `UNIQUE constraint failed`).
  - Căutare integrată în `AddTreatmentScreen` (minim 3 caractere, listă derulabilă înălțime fixă
    nu top-5 tăiat) — **deduplicare pe produs** (nume+DCI+concentrație+formă): Nomenclatorul are un
    rând per **ambalaj**, nu per medicament — fără dedup, aceeași "AUGMENTIN 500mg/125mg" apărea de
    5 ori identic (cutii diferite), imposibil de diferențiat vizual, irelevant pentru scopul de aici.
- **4 câmpuri noi pe tratament** (inspirate din Medisafe/MyTherapy, cercetate live): `formaFarmaceutica`
  (pre-completată din Nomenclator), `cantitate` (separată de `dosage`/concentrație — "2 comprimate"
  vs. "500mg"), `indicatie` (motivul tratamentului — se leagă de decizia academică nerezolvată
  „boală cronică vs. polimedicație"), `instructiuni` (text liber). Toate opționale.
- **Bug-uri reale găsite la testarea pe device, fixate în aceeași sesiune**:
  1. Fereastra de acțiune lipsea — o doză se putea confirma/omite oricând, indiferent cât de departe
     de ora programată. Fix: `isDoseActionable` (±60 min), aplicat atât în `LogDoseUseCase`
     (protejează și acțiunile din notificare) cât și în UI (`TodayScreen` ascunde butoanele în
     afara ferestrei). `MarkOverdueDosesUseCase` declanșat acum și la fiecare intrare pe „Azi", nu
     doar din workerul periodic de 6h.
  2. Istoricul arăta mereu ora **programată**, niciodată ora **reală** la care a fost luată doza
     (`DoseLog.takenAt` exista deja, doar UI-ul nu-l folosea). Fix: `TreatmentDetailScreen`/
     `PatientDetailScreen` arată „programat HH:mm · Luat HH:mm" pentru dozele TAKEN.
  3. Cantitate unică per tratament — nu putea modela „Nolpaza dimineața 1 comprimat, seara 2
     comprimate, la amiază nimic". Verificat explicit înainte de a alege soluția: PDC/MPR
     (`AdherenceCalculator.compute`) se calculează agregat pe zi la nivel de doză, nu per
     tratament — alegerea de model nu afectează corectitudinea academică. Model ales (nu split în
     tratamente separate): `Treatment.times: List<LocalTime>` → `schedule: List<DoseSlot>`
     (oră + cantitate proprie opțională), `times` rămâne proprietate **derivată** (cod care doar
     citește orele nu s-a rupt — doar 5 situri reale de construcție `Treatment(...)` în tot codul).
     `DoseLog` capătă propriul `cantitate`, **snapshot la generare** (`GenerateDosesUseCase`) — NU
     legat live de tratament, spre deosebire de `medicationName`/`dosage` (inconsecvență
     preexistentă, notată în cod, nereparată acum). Stocare: `TreatmentEntity.slotCantitateCsv`
     (separator `;`, nu `,` — cantitate poate conține virgulă zecimală), aliniat pozițional cu
     `timesCsv`.
- **Schema**: `PillProntoDatabase` v4→v5 (cele 4 câmpuri) →v6 (`slotCantitateCsv`+`DoseLog.cantitate`).
  `supabase/migrations/0008_treatment_extra_fields.sql` + `0009_dose_slot_cantitate.sql` (noi, de
  rulat manual de utilizator, după 0001-0007).
- **Teste noi**: `NomenclatureImporterTest`, `NomenclatureDaoTest` (instrumentat, FTS diacritic-insensitive
  confirmat pe SQLite real), `SearchNomenclatureUseCaseTest`, `DoseActionWindowTest`,
  `LogDoseUseCaseTest`, `GenerateDosesUseCaseTest`, `MappersTest` extins — toate trec.
- **Testare notificări confirmată**: reminder cu buton Confirmă din notificare testat peste noapte,
  funcțional (inclusiv fereastra de acțiune).

### Faza 2b-i — Scanare GS1 DataMatrix + catalog `gtin_mappings` (implementat — 2026-09-10)

**⚠️ Necomis încă** — pe branch `feature/faza2b-i-gs1-scan` (mai multe commit-uri locale), compilat
+ toate testele unitare trec local (`testDebugUnitTest` + `assembleDebug`), instalat repetat pe
device. Testele instrumentate rămân de rulat de utilizator. Migrările `0010`/`0011` **ne-rulate**.

- **Scanare**: reutilizat 100% mecanismul de `GmsBarcodeScanning` (Play Services Code Scanner) deja
  folosit pt. codul QR de invitație (1.5d) — `ui/treatments/ScanBarcode.kt::scanMedicationBarcode`,
  suportă `FORMAT_DATA_MATRIX` (cutii UE, mandatat FMD) + formate liniare EAN-13/EAN-8/UPC-A
  (fallback). **Nicio dependență Gradle nouă, niciun `CAMERA` nou în manifest, fără CameraX**
  (rezervat Fazei 3 — feed continuu, caz de utilizare diferit de un scan single-shot).
- **`domain/gs1/Gs1Parser.kt`** (domain pur, fără dependențe Android): parsează payload-ul GS1
  DataMatrix după Application Identifiers — AI `01` GTIN (14 cifre fix), `17` expirare YYMMDD
  (fix), `10` lot (variabil ≤20), `21` serial (variabil ≤20), cele 4 mandatate FMD. **Documentat cu
  surse oficiale** (Regulamentul Delegat (UE) 2016/161 + ghidul GS1 Healthcare de implementare
  FMD — vezi `eu2016161`/`gs1fmd2016` în `Lucrare_Disertatie/thesis.bib`, secțiunea "Partea III"):
  codul GS1 DataMatrix (serializare/trasabilitate) e obligatoriu **doar** pe medicamentele Rx
  (+ excepții Anexa I/II), spre deosebire de codul de bare comercial (EAN-13), obligatoriu pe toate
  — confirmă că `extractGtin`/`ScanBarcode.kt` (formate liniare ca fallback) modelează corect
  ambele cazuri reale. Confirmă și decizia deja stabilă: EMVS rămâne restricționat la actori
  autorizați din lanțul de distribuție, aplicația NU îl interoghează.
  - **Bug real găsit + fixat la testarea pe device**: primul test a eșuat — codul era corect
    DataMatrix (`format=16`), dar payload-ul are un caracter FNC1/GS literal (`0x1D`) **înaintea**
    primului AI (Play Services Code Scanner nu-l elimină el însuși), pe care parserul nu-l
    anticipa. Fix: `Gs1Parser.parse()` elimină un eventual prefix GS înainte de a parsa. Confirmat
    cu 2 payload-uri reale capturate prin logging temporar (`adb logcat`) de pe cutii fizice,
    păstrate ca teste de regresie în `Gs1ParserTest` (GTIN e dată de produs public, nu personală).
    Retestat pe device după fix — **confirmat funcțional** de utilizator (scan → recunoaștere OK).
- **`ui/gtinmapping/AssociateGtinScreen`+`ViewModel`** — ecran dedicat (buton „Asociere coduri
  (GTIN)" în tab-ul Cont, **vizibil DOAR pt. contribuitori de încredere** —
  `state.profile?.isTrustedContributor == true`, gating adăugat după ce userul a semnalat că
  butonul apărea și pe un cont netrusted): scan → caută în Nomenclator → alege → salvat → gata pt.
  următorul, **fără să creeze un tratament** (spre deosebire de fluxul din `AddTreatmentScreen`,
  disponibil tuturor). `NomenclatureSuggestions` extras din `AddTreatmentScreen.kt` în
  `core/ui/components/NomenclatureSuggestionsList.kt` (acum reutilizat din 2 ecrane).
- **Tabel local `gtin_mappings`** (`PillProntoDatabase`, NU `NomenclatureDatabase` — acolo s-ar
  pierde la orice reimport al Nomenclatorului): GTIN scanat → Cod CIM, construit progresiv — la un
  scan cu GTIN necunoscut, userul alege manual din sugestii, iar acea alegere „învață" maparea.
  **Seed static livrat în APK** (`assets/gtin_mappings_seed.tsv`, gol deocamdată) +
  `GtinMappingSeedImporter`: flag VERSIONAT în SharedPreferences (nu `count()>0` — tabelul mai
  crește organic din confirmările userilor) + insert cu `IGNORE` (nu suprascrie niciodată).
- **Extindere — catalog partajat în Supabase** (decizie luată după ce userul a întrebat despre un
  rol de „admin"/contribuitor, discuție despre PHARMACIST auto-declarat vs. încredere reală):
  `gtin_mappings` **nu mai e strict local** — există acum și un tabel Supabase omonim, **primul
  tabel cu adevărat public din schema proiectului** (toate celelalte sunt scopate pe rând propriu
  sau lanț `patient_profile_id`/`links`). SELECT deschis `anon`+`authenticated` (date de produs
  public, nu de sănătate — păstrează „aplicația funcționează fără login"). Scriere **doar** prin
  funcția `SECURITY DEFINER` `contribute_gtin_mapping` (pattern identic `claim_link`, migrarea
  0007) — verifică `profiles.is_trusted_contributor` (coloană nouă, **NU** un rol nou, un
  capability-flag ortogonal la `AccountRole`, setat manual de dezvoltator direct în Supabase, NU
  prin auto-declarare — evită exact problema PHARMACIST-ului neverificat). `GtinCatalogSyncManager`
  trage (`pull`) tot tabelul, necondiționat, pt. toți userii (spre deosebire de `SyncManager`
  existent, strict `AccountRole.PATIENT`) — `REPLACE` peste o ghicire locală neconfirmată.
  `ContributeGtinMappingUseCase` (folosit acum de `AssociateGtinViewModel` în loc de
  `ConfirmGtinMappingUseCase` direct) confirmă local **întotdeauna** + propagă la catalogul comun
  **doar** dacă userul e contribuitor de încredere — best-effort, eșec de rețea nu anulează local.
  Migrarea `supabase/migrations/0011_gtin_mappings_catalog.sql` (nouă, de rulat manual, după 0010).
- **`Treatment.codCim`** (câmp nou, opțional, separat de `gtin_mappings`): trasabilitate la
  intrarea Nomenclator exactă aleasă — **acesta se sincronizează** (tratamentele deja se
  sincronizează) → migrarea `0010_treatment_cod_cim.sql`.
- **Schema Room**: `PillProntoDatabase` v6→v7 (`GtinMappingEntity` + `TreatmentEntity.codCim`).
- **Teste noi**: `Gs1ParserTest` (inclusiv 2 payload-uri reale de pe device), `ScanBarcodeTest`,
  `LookupTreatmentByGtinUseCaseTest`, `ConfirmGtinMappingUseCaseTest`, `ContributeGtinMappingUseCaseTest`,
  `GtinCatalogSyncManagerTest`, `GtinMappingSeedImporterTest`, `AssociateGtinViewModelTest`,
  `MappersTest` extins, `GtinMappingDaoTest` (instrumentat, extins: `upsertAll`/`insertSeedBatch`)
  — toate unitare trec local; instrumentat de rulat pe device.
  **Nu există `AddTreatmentViewModelTest`** — blocaj arhitectural preexistent, nu introdus acum:
  `AddTreatmentViewModel` depinde de `ReminderCoordinator` (clasă concretă, nu interfață), care la
  rândul lui construiește `ReminderScheduler` cu un `Context` Android real (`AlarmManager`) chiar în
  constructor — imposibil de instanțiat într-un test JVM pur fără Robolectric/Mockito (niciuna
  configurată în proiect, convenția fiind fake-uri scrise de mână pe interfețe).
- **Extindere — dată expirare la scanare + alerte** (cercetare GS1/FMD → `Treatment.expiryDate`
  nou, din AI `17` al codului DataMatrix, `ScanBarcode.kt::extractGtin` → `extractScannedBarcode`
  (întoarce `ScannedBarcode(gtin, expiryDate)`, ambele ecrane de scanare actualizate)): afișare
  colorată (`DoseDueNow`/`DoseMissed`, prag `NEAR_EXPIRY_DAYS_THRESHOLD = 14` zile, definit în
  `domain/model/Treatment.kt`, reutilizat identic de UI și de alertă — o singură sursă de adevăr)
  în `AddTreatmentScreen` + `TreatmentDetailScreen`. **Alertă locală periodică** (nu doar afișare
  pasivă): `ExpiryAlertWorker` (12h) + `ExpiryAlertChecker` (logică pură, deduplicare pe cheie
  compusă `treatmentId:expiryDate:stage` — o rescanare cu altă expirare capătă automat propriile
  alerte, fără reset explicit) + `ExpiryAlertNotifier` (canal propriu) — toate mirror 1:1 pe
  `CaregiverAlertWorker`/`MissedDoseChecker`/`CaregiverAlertNotifier` deja existente. Se
  sincronizează (`expiryDate`, ca `codCim`) → migrarea `0012_treatment_expiry_date.sql`. Schema
  Room v7→v8.
- **De făcut sesiunea viitoare**: userul rulează manual migrările `0010`, `0011`, `0012` (în
  ordine), se marchează contribuitor de încredere (SQL direct: `update profiles set
  is_trusted_contributor = true where id = '<uid>'`), testează pe device: confirmarea unei mapări
  + verificare apariție rând în `gtin_mappings` (Supabase Table Editor) + pull pe alt cont/device;
  scanarea unei cutii cu dată de expirare (culoare corectă în `AddTreatmentScreen`/
  `TreatmentDetailScreen`). Fallback fuzzy la căutare deja testat live (vezi mai jos; OCR a fost
  eliminat). De investigat separat: crash-ul `NavigationSmokeTest`. Apoi commit + push + PR + merge.

### Faza 2b-ii — OCR fallback: implementat, testat, ELIMINAT (2026-09-10)

Implementat complet (poză via `ACTION_IMAGE_CAPTURE`, fără CameraX + `com.google.mlkit:text-recognition`,
rezultatul alimenta căutarea Nomenclator existentă — vezi istoricul git, commit `eb92ceb`, pentru
detaliile tehnice complete) și **testat live pe device de user** — funcțional ca mecanism (a dus
direct la găsirea bug-ului de căutare fuzzy de mai jos), dar cu **rată de succes prea scăzută în
practică** la identificarea corectă a medicamentului din poză, la aprecierea userului după
testare reală. Decizie: **eliminat complet** la această etapă — rămân doar cele două metode de
identificare cu fiabilitate confirmată: scanare cod (Faza 2b-i) + introducere manuală de text
(căutare Nomenclator, acum cu fallback fuzzy, vezi mai jos). Cod șters: `ui/treatments/OcrCapture.kt`
+ testul lui, `AddTreatmentViewModel.onOcrTextRecognized`/`ocrFailed`, butonul din
`AddTreatmentScreen`, dependența `com.google.mlkit:text-recognition`, cache-path-ul `ocr_captures`
din `FileProvider`. **Notă pt. teză**: un rezultat negativ documentat (OCR simplu, fără
crop/enhance, insuficient de fiabil pe text de cutie de medicament) — motivează concret de ce
arhitectura stabilă a proiectului prevede oricum OCR doar ca element dintr-o cascadă mai largă
(Viziune→OCR→cod 2D, Faza 3+), nu ca mecanism de sine stătător. Nu exclude o reîncercare ulterioară
cu o abordare mai robustă (ex. `GmsDocumentScanning` cu crop/enhance, sau OCR ca *narrowing* în
cascada Fazei 3, nu ca sursă unică de decizie).

### Căutare Nomenclator — fallback fuzzy (Levenshtein) (implementat — 2026-09-10)

- **Bug real găsit prin testarea OCR-ului pe device**: OCR a citit "Algocalnin" în loc de
  "Algocalmin" (o literă confundată, tipic OCR: m/n, l/1, O/0) — căutarea Nomenclator (FTS4,
  potrivire de **prefix exact**) nu găsea NIMIC, pentru că "algocalmin" nu începe literal cu
  "algocalnin" (diverg la caracterul 8). Aceeași căutare deservește și tastarea manuală din
  `AddTreatmentScreen`, deci fix-ul ajută ambele fluxuri, nu doar OCR-ul.
- **`domain/util/Levenshtein.kt`** (pur, fără dependențe Android) — distanța Levenshtein clasică
  (DP, 2 rânduri).
- **`NomenclatureRepositoryImpl.search()`** — fallback în 2 pași, STRICT când căutarea exactă
  întoarce 0 rezultate (comportamentul existent, cu rezultate, rămâne neschimbat): (1) adună
  candidați printr-un prefix SCURT (~4 caractere din primul token, mai tolerant decât prefixul
  complet) — tot prin FTS4 existent, nu scanare completă a celor 32.500+ rânduri; (2) rangă
  candidații după distanța Levenshtein față de **primul cuvânt** din query, comparat cu **primul
  cuvânt** din `denumireComerciala` (nu șirul întreg, care conține și dozajul, ex. "500mg" — bug
  găsit și fixat chiar în timpul implementării: comparat cu șirul întreg, un query scurt de un
  cuvânt avea mereu distanță mare, doar din diferența de lungime). Prag proporțional cu lungimea
  (~30%, minim 1).
- **Limitare cunoscută, acceptată**: dacă OCR greșește chiar în primele caractere, fallback-ul tot
  nu găsește nimic (prefixul scurt de candidați cere primele caractere corecte) — acoperă cazul
  realist raportat (greșeală la mijlocul/sfârșitul cuvântului), nu orice greșeală posibilă. O
  scanare completă a Nomenclatorului ar elimina și limitarea asta, dar cu cost de performanță
  nejustificat pt. cazul comun.
- **Bug de tooling găsit + fixat separat, la rularea testelor instrumentate**: testele noi
  (`GtinMappingDaoTest` extins, `NomenclatureRepositoryImplTest` nou) foloseau nume de test în
  backtick cu spații (convenția din testele unitare) — la împachetarea DEX (`minSdk=26`), D8
  respinge clasele lambda generate de `runTest {}` care conțin spații în nume ("Space characters
  in SimpleName... not allowed prior to DEX version 040"). Afectează DOAR testele instrumentate
  (`androidTest`, DEX-compilate), nu cele unitare (JVM pur, niciodată DEX-compilate) — fix:
  redenumite fără spații (camelCase/underscore, ca `NomenclatureDaoTest` deja existent). Găsit prin
  rulare reală `connectedDebugAndroidTest` de pe acest device — toate cele 10 teste
  (`GtinMappingDaoTest` + `NomenclatureRepositoryImplTest`) trec acum, inclusiv scenariul exact
  raportat ("Algocalnin" → găsește "Algocalmin").
- **Notă separată, nelegată de munca curentă**: `connectedDebugAndroidTest` pe TOATĂ suita a
  crăpat la `NavigationSmokeTest` (`MainActivity`, eroare Hilt — "component was not created,
  check HiltAndroidRule") — **neinvestigat, posibil preexistent** (nu s-a atins acest fișier azi).
  De verificat separat, altă sesiune.
- **Teste noi**: `LevenshteinDistanceTest` (unitar), `NomenclatureRepositoryImplTest` (instrumentat,
  Room+FTS4 reale — singurul mod de a testa fallback-ul, nu poate fi simulat cu un fake) — toate
  trec, verificate live pe device.

### Faza 3a-i — CameraX: feed live + permisiune, fără ML încă (implementat, testat — 2026-09-10)

Primul pas din Faza 3 (Viziune). Decizie luată cu utilizatorul: nu se așteaptă dataset-ul propriu
de cutii RO/UE (nu există încă) — se validează întâi pipeline-ul tehnic (CameraX → inferență
on-device → overlay) cu un model generic preantrenat (clase COCO), împărțit pe bucăți testabile
separat, ca la Faza 2: **3a-i** (acest pas, doar feed cameră) / **3a-ii** (viitor, model LiteRT
YOLO11n-seg + decodare ieșire + overlay contururi gri).

- **CameraX Compose-nativ**, `androidx.camera:camera-core`/`camera-camera2`/`camera-lifecycle`/
  `camera-compose`, versiune **1.6.2** (confirmată exact prin `maven-metadata.xml`, nu prin căutare
  indirectă — 1.7.0 e doar alpha). `CameraXViewfinder` (pachet `androidx.camera.compose`) +
  `ImplementationMode` (pachet **`androidx.camera.viewfinder.core`**, nu `...viewfinder.surface`
  cum ar sugera unele exemple vechi/AI-generate — verificat direct din `.aar`-ul real din cache-ul
  Gradle local la primul build eșuat cu „Unresolved reference"). `SurfaceRequest` din
  `androidx.camera.core`.
- **`ui/vision/CameraPreview.kt`** — Composable reutilizabil: `Preview.Builder()` +
  `ProcessCameraProvider.getInstance(context)` + `bindToLifecycle(lifecycleOwner,
  CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase)`, bind/unbind automat via `DisposableEffect`
  scopat pe `LocalLifecycleOwner` — fără nicio analiză de frame-uri sau model de inferență (acelea
  intră în 3a-ii).
- **`ui/vision/VisionScanScreen.kt`** — ecran nou, punct de intrare necondiționat (fără gating pe
  rol — doar preview, fără scriere de date) din `AccountScreen`, lângă „Asociere coduri (GTIN)",
  marcat explicit „(experimental)". Cere `CAMERA` runtime **la intrarea pe ecran**, nu la pornirea
  aplicației (spre deosebire de `POST_NOTIFICATIONS` din `MainActivity`) — prima permisiune de
  cameră din proiect (scanarea de coduri/OCR-ul eliminat delegau mereu la componente
  self-permisionate: `GmsBarcodeScanning`, `ACTION_IMAGE_CAPTURE`). Fallback „permisiune refuzată"
  → text + buton spre setările aplicației (`Permissions.openAppSettings`, funcție nouă, pattern
  generic reutilizabil pt. orice permisiune refuzată definitiv „nu mai întreba").
- **Manifest**: `android.permission.CAMERA` + `<uses-feature android:name="android.hardware.camera"
  android:required="false" />` (nu blochează instalarea pe device-uri fără cameră spate).
- **Fără teste automate** — plumbing pur de cameră, netestabil semnificativ în JVM fără Robolectric
  (neconfigurat în proiect); verificare **doar manuală, pe device fizic**: feed live confirmat OK
  (fără lag/crash), refuz permisiune confirmat OK (fallback fără crash), intrare/ieșire repetată pe
  ecran confirmată OK (fără resursă de cameră blocată).
- **`scripts/export-yolo-seg-model.py`** — script Python separat (rulat local de utilizator, NU
  parte din build-ul Gradle, cere `pip install ultralytics`): descarcă `yolo11n-seg.pt` preantrenat
  și îl exportă la LiteRT (`.tflite`, `model.export(format="tflite")`). Pregătit acum pt. Faza
  3a-ii, **neconsumat încă** de aplicație în acest stadiu.
### Faza 3a-ii — model LiteRT (YOLO-seg) + decodare (doar cutii) + overlay (implementat — 2026-09-11)

**Decizie de scop, luată cu utilizatorul**: în acest pas se decodează DOAR tensorul de detecție
(cutii + clase + scor), FĂRĂ măștile de segmentare (protos 160×160×32 + coeficienți + resize) —
acelea rămân pt. **Faza 3a-iii** (viitor), după ce se confirmă că restul pipeline-ului
(CameraX→LiteRT→overlay) funcționează corect. Contur gri = dreptunghi (bounding box), nu formă
exactă de segmentare, la această etapă.

- **LiteRT**: `com.google.ai.edge.litert:litert:2.2.0` (confirmat "Latest" direct din
  `maven-metadata.xml`, nu din research indirect). API `CompiledModel` (clasa veche `Interpreter`
  a fost **eliminată**, nu doar deprecată, în LiteRT 2.0+). Semnătura fără parametru `env` explicit
  (`CompiledModel.create(context.assets, "model.tflite", CompiledModel.Options(Accelerator.CPU))`)
  — confirmată printr-un exemplu real de cod dintr-un issue GitHub oficial (documentația arăta și
  o variantă cu `env` suplimentar, dar fără detalii complete despre construcția lui) — **compilează
  corect**, confirmă alegerea. AGP-ul proiectului (9.3.2) adaugă automat `.tflite` la
  `noCompress` — fără config manuală.
- **`domain/vision/`** (pur Kotlin, testabil JVM, ca `domain/gs1`/`domain/util`): `Detection.kt`
  (`RectF01` cx/cy/w/h normalizate), `CocoLabels.kt` (cele 80 clase COCO), `NonMaxSuppression.kt`
  (greedy, IoU), `YoloOutputDecoder.kt` (decodează layout-ul **channel-first** al tensorului de
  detecție `[1, 4+numClasses, numAnchors]` — pt. ancora `a`, canalul `c` e la indexul
  `c*numAnchors+a`), `LetterboxMapper.kt` (inversează letterbox-ul din preprocesare, mapează
  cutiile din spațiul 640×640 al modelului înapoi în spațiul normalizat al imaginii originale).
  **8 teste unitare noi** (`YoloOutputDecoderTest`, `LetterboxMapperTest`) — verifică matematica
  (extragere layout, filtrare prag, NMS, cele 3 cazuri de padding letterbox) **înainte** de a
  scrie partea Android — toate trec.
- **`ui/vision/YoloSegModel.kt`** — glue Android (Context/Bitmap/LiteRT), documentat ca excepție
  de la separarea strictă ui/domain (precedent `ScanBarcode.kt`/`GoogleSignInHelper.kt`, secțiunea
  4): încarcă modelul din assets (`yolo11n_seg.tflite`, nume fix, **comis în repo** — 11.8MB, la
  fel ca `nomenclator.tsv.gz`/`gtin_mappings_seed.tsv`, altfel o clonă curată n-ar avea feature-ul
  funcțional fără pasul manual de export), letterbox-resize la 640×640, `model.run()`, decodare.
- **`ui/vision/DetectionOverlay.kt`** — `Canvas` Compose, dreptunghiuri gri (culoarea `DoseUnknown`
  din temă — exact reutilizarea anticipată în secțiunea 3 pt. AR) + etichetă (clasă COCO +
  confidence, util pt. validare vizuală pe un ecran oricum "experimental"). Mapare cu formulă
  **crop-to-fill** (`scale = max(...)`) — **confirmată corectă empiric pe device** (dreptunghiurile
  se aliniază corect cu obiectele reale).
- **`ui/vision/CameraPreview.kt`** — extins cu un al doilea use case `ImageAnalysis` opțional
  (`RGBA_8888` + `imageProxy.toBitmap()`, evită conversia manuală YUV; `STRATEGY_KEEP_ONLY_LATEST`
  = backpressure automat, fără throttling manual), legat alături de `Preview` în același
  `bindToLifecycle(...)`.
- **`ui/vision/VisionScanScreen.kt`** — instanțiază `YoloSegModel` o singură dată (`DisposableEffect`,
  închis la dispose, `runCatching` — degradare grațioasă cu banner „Model AI lipsă" dacă `.tflite`-ul
  nu există încă în assets) + `Executor` dedicat pt. analiza de frame-uri (NU main thread —
  `CompiledModel.run()` e blocant). Rotește bitmap-ul cu `imageInfo.rotationDegrees` înainte de
  inferență (`ImageAnalysis` nu pre-rotește bufferul).
- **Limitare reală de mediu, găsită live (2026-09-11)**: exportul Ultralytics la LiteRT **nu
  rulează pe Windows nativ** — `AssertionError: LiteRT export only supported on Linux x86 and
  macOS`, restricție hard-codată în unealta de export (fișierul `.tflite` rezultat rulează normal
  pe orice platformă, inclusiv Android — doar procesul de export cere Linux/macOS). Utilizatorul a
  folosit **Google Colab** (zero instalare locală) — funcțional, `.tflite` exportat cu succes.
  `scripts/export-yolo-seg-model.py` documentează ambele alternative (Colab + WSL2, dacă cineva
  preferă local pe viitor).
- **Bug real găsit + fixat prin testare live pe device (2026-09-11) — cel mai semnificativ din
  această fază**: prima rulare pe device arăta feed-ul de cameră, dar NICIO detecție, niciodată,
  indiferent de obiect. Diagnosticat prin logging temporar iterativ direct pe device (scor maxim
  brut per frame, min/max global pe tensor, max per canal din cele 116, salvare pe disk +
  inspecție vizuală a imaginii preprocesate) — toate arătau o imagine de intrare vizual perfect
  corectă (letterbox, rotație, culori), dar scoruri de clasă mereu aproape de zero (niciodată
  peste prag). **Root cause găsit prin comparație directă cu Ultralytics** (rulat în Colab pe
  ACEEAȘI imagine extrasă de pe device, `adb pull` + `SendUserFile`): Ultralytics obținea
  încredere >0.85 pe acea imagine, deci modelul + imaginea erau amândouă corecte — bug-ul era
  exclusiv în codul de preprocesare Kotlin. Confirmat citind direct din codul sursă Ultralytics
  (`nn/modules/head.py`, `Segment._inference`) că ordinea canalelor tensorului de ieșire e
  `[cutie(4), scoruri clasă(80, sigmoid deja aplicat la export), coeficienți mască(32)]` — exact ce
  implementasem — deci decodarea (`YoloOutputDecoder`) era corectă de la bun început. Bug-ul real:
  `interpreter.get_input_details()` (rulat de user în Colab, pe fișierul `.tflite` real) a arătat
  `shape=[1, 3, 640, 640]` — **input NCHW (planuri R/G/B separate), NU NHWC (`[1,640,640,3]`,
  RGB interleaved per pixel) cum presupusese `bitmapToNormalizedFloatArray`** — quirk al
  exportului `onnx2tf` folosit de Ultralytics pt. TFLite, care poate păstra layout-ul nativ
  PyTorch (NCHW) în loc de convenția TF "standard" (NHWC). Array-ul avea dimensiunea corectă
  (1228800 floats), deci nu arunca nicio eroare — doar conținutul era complet amestecat spațial
  pentru rețea. Fix: `bitmapToNchwFloatArray` (redenumită), scrie 3 planuri separate (tot R, apoi
  tot G, apoi tot B) în loc de interleaved per pixel. **Retestat pe device — confirmat funcțional**:
  dreptunghiuri gri + etichetă corectă pe obiecte uzuale (COCO). Pe cutia de medicamente NU apare
  nimic — **comportament AȘTEPTAT, nu bug**: COCO (clasele acestui model generic) nu are o clasă
  „cutie de medicamente" — exact motivul pt. care Faza 4 (embeddings + enrollment) există, acest
  model preantrenat era doar pt. validarea pipeline-ului tehnic, nu pt. recunoaștere reală.
  **Lecție reținută, adăugată la convenția de debugging a proiectului**: la un model ML "care
  rulează dar nu produce rezultate corecte" (nu crapă, doar iese greșit), comparația directă cu
  rularea de referință a bibliotecii originale (Ultralytics/PyTorch) pe EXACT aceeași imagine
  extrasă din pipeline e mult mai eficientă decât ghicitul succesiv de ipoteze — a confirmat rapid
  că problema era 100% în preprocesarea Kotlin, nu în model/imagine, înainte de a găsi bug-ul exact.
- **PR #13 mergeuit pe `main`** (2026-09-11), branch `feature/faza3a-ii-litert-detect` șters
  (local + `origin`). Faza 3a-ii e considerată închisă; Faza 3a-iii (măști de segmentare) pornește
  pe branch nou.

### Faza 3a-iii — măști de segmentare (contur real, nu doar bounding box) (implementat, testat live pe device — 2026-09-11)

**Decizie de scop, luată cu utilizatorul**: fără niciun contur poligonal calculat explicit
(marching squares etc.) — masca se desenează ca bitmap semi-transparent colorat (tenta
`DoseUnknown`) exact peste dreptunghiul deja mapat pe ecran; marginea vizuală a zonei translucide
E conturul, mult mai simplu decât extragerea unui `Path` și suficient pt. validarea vizuală cerută
la această etapă. Dreptunghiul gri + eticheta rămân desenate ca ghidaj.

- **Model matematic** (convenția YOLOv8/11-seg, standard Ultralytics): tensorul de detecție
  (output 0) are de fapt `4+numClasses+maskDim` canale (`maskDim=32`), nu doar `4+numClasses` —
  codul din Faza 3a-ii deja "tolera" asta din greșeală (verifica doar un bound minim, ignora tacit
  canalele finale). Al 2-lea tensor de ieșire (output 1) = "proto-măști", `[1,32,160,160]`
  **channel-first**. Masca finală per detecție = `sigmoid(coeficienți(32) · proto(32,y,x))` per
  pixel, threshold 0.5 → boolean, calculată DOAR pe regiunea proto-pixelilor corespunzătoare cutiei
  (crop, nu tot grid-ul 160×160) — cutia (0..1 normalizată relativ la modelul 640×640) indexează
  direct în proto (160×160 e aceeași imagine, doar rezoluție mai mică), fără transformare
  suplimentară. Crop-ul se face cât timp cutia e încă în spațiul modelului (imediat după
  `YoloOutputDecoder.decode`, ÎNAINTE de `LetterboxMapper.mapToOriginalImage`) — grid-ul mic
  rezultat e cărat neschimbat prin `.copy(box=...)` (care păstrează câmpurile nespecificate), apoi
  întins direct peste dreptunghiul FINAL la randare, fără nicio transformare inversă de letterbox.
- **`domain/vision/Detection.kt`**: `SegMask(width, height, values: List<Boolean>)` (List, nu
  BooleanArray, pt. `equals` structural gratuit — cost de autoboxing neglijabil la dimensiunile
  astea, crop nu grid întreg). `Detection` capătă `maskCoeffs: List<Float>? = null` (tranzitoriu,
  cei 32 coeficienți bruți) + `mask: SegMask? = null` (final) — ambele opționale, fără breaking
  change pe testele existente din 3a-ii.
- **`domain/vision/YoloOutputDecoder.kt`**: `decode(...)` capătă `maskDim: Int = 0` (default =
  comportament identic 3a-ii); extrage `maskCoeffs` din canalele finale când `maskDim > 0`.
- **`domain/vision/MaskDecoder.kt`** (nou, pur, testabil): `decode(...)` (dot-product+sigmoid+
  threshold pe crop, clamped la limitele grid-ului, minim 1×1 chiar pt. obiecte foarte mici/
  departate) + `attach(...)` (populează `mask` pe lista de detecții, golește `maskCoeffs` odată
  consumați). **6 teste noi** (`MaskDecoderTest`) + 2 teste noi în `YoloOutputDecoderTest` — toate
  trec.
- **`ui/vision/YoloSegModel.kt`**: constante noi `MASK_DIM=32`, `PROTO_SIZE=160`; citește și
  `outputBuffers[1]` (protos) — degradare grațioasă (fallback la `maskDim=0`, doar cutii, ca în
  3a-ii) dacă modelul n-are al 2-lea output.
- **`ui/vision/DetectionOverlay.kt`**: pt. fiecare `detection.mask != null`, construiește un
  `Bitmap` mic din grid-ul boolean (gri translucid ~40% alpha unde `true`, transparent unde
  `false`) și îl întinde (`drawBitmap`) exact peste dreptunghiul deja calculat pe ecran.
- **Verificare empirică pe device (2026-09-11), confirmată din prima încercare** (spre deosebire de
  bug-ul NCHW din 3a-ii): logging temporar (`Log.e`, șters după confirmare) a arătat
  `outputBuffers.size=2`, `output0.size=974400` (=116×8400 exact) și `output1.size=819200`
  (=32×160×160 exact) — presupunerile de layout confirmate la nivel de dimensiuni. Verificare
  vizuală pe ecranul "Scanare vizuală (experimental)": masca (zonă translucidă gri) urmărește
  vizibil forma reală a obiectului, nu doar dreptunghiul — **confirmat funcțional de utilizator,
  fără nevoie de debugging suplimentar** (channel-first-ul presupus pt. proto s-a dovedit corect
  din prima, spre deosebire de input-ul NCHW din 3a-ii care a cerut o sesiune întreagă de debugging).
- **PR #14 mergeuit pe `main`** (2026-09-11), branch `feature/faza3a-iii-litert-masks` șters
  (local + `origin`).

### Optimizare — accelerator GPU pt. inferență LiteRT (implementat, testat live pe device — 2026-09-11)

**Context**: utilizatorul a semnalat overlay (contur+mască) sacadat/cu întârziere față de obiectul
real — NU feed-ul de cameră (`Preview` rulează deja fluid, independent de rata de analiză, vezi
`STRATEGY_KEEP_ONLY_LATEST` în `CameraPreview.kt`), ci strict rata la care se termină
`YoloSegModel.detect()` per cadru analizat.

- **`YoloSegModel.kt` forța `CompiledModel.Options(Accelerator.CPU)`** — inspectând direct
  bytecode-ul `.aar`-ului LiteRT 2.2.0 din cache-ul Gradle local (nu presupunere), enum-ul
  `Accelerator` are de fapt `NONE, CPU, GPU, NPU`, iar runtime-ul deja bundle-uiește
  `libLiteRtClGlAccelerator.so` (OpenCL/OpenGL) pt. `arm64-v8a`/`armeabi-v7a`/`x86_64` — GPU
  delegate era deja disponibil în dependința existentă, doar nefolosit. **Nicio schimbare de
  dependențe Gradle.**
- **Măsurat pe device, înainte/după** (instrumentare temporară de timp în `detect()`, ștearsă după
  confirmare — convenția proiectului): **CPU: ~450-500ms/cadru (~2-2.3 fps)** →
  **GPU: ~85-120ms/cadru (~9-12 fps)** — **~5x mai rapid**, confirmă exact cauza lag-ului semnalat.
- **`createModel(context)`** (nou, în `YoloSegModel`): încearcă întâi `Accelerator.GPU`, `catch
  (e: Throwable)` (la fel de larg ca `runCatching` deja folosit în `VisionScanScreen` pt.
  încărcarea modelului) → fallback grațios la `Accelerator.CPU` dacă delegate-ul eșuează la
  compilare pe un anumit device (nu toate GPU-urile mobile suportă la fel de bine OpenCL/OpenGL).
  Pe acest device: GPU a reușit direct, fără fallback. 2 loguri `Log.w` permanente (create model)
  arată ce accelerator rulează efectiv.
- **Fără teste noi** — cod glue Android/LiteRT (alegere accelerator), netestabil semnificativ în
  JVM, ca restul Fazei 3a; nimic din `domain/vision/` (pur, testat) s-a atins.
- **Alternative discutate, nu implementate acum** (dacă GPU nu ar fi fost suficient): cuantizare
  INT8 la export (reduce costul real per cadru, nu doar mută treaba pe alt silicon) și/sau
  rezoluție de input mai mică (640→416/320, trade-off real de calitate pe obiecte mici).
- **Pe același branch/PR** (`feature/faza3a-iii-litert-masks`, PR #14, mergeuit pe `main`
  împreună cu măștile de segmentare).
- **Decizie luată cu utilizatorul (2026-09-11)**: rămânem pe YOLO11n-seg deocamdată, NU trecem la
  YOLO26 (succesorul Ultralytics, lansat ianuarie 2026, +3.7 mask AP și ~35% mai rapid pe CPU față
  de YOLO11n pe hârtie) — cercetare (nu presupunere) a găsit riscuri reale nerezolvate specifice
  combinației noastre (segmentare + TFLite/LiteRT + Android + GPU delegate): delegate-ul GPU
  eșuează pe Android cu modele YOLO26 exportate la TFLite (operatori nesuportați la LiteRT ~2.1,
  issue închis "not planned" de Ultralytics/LiteRT — ar pune în pericol exact accelerarea GPU de
  mai sus), modulul de proto-măști (`Proto26`) e arhitectural diferit (nu un swap simplu de
  fișier `.tflite`, ar cere reverificare empirică de la zero ca la bug-ul NCHW), plus bug-uri de
  export recente specifice segmentării (INT8, rezoluție mască). Motivare suplimentară: modelul
  COCO generic e oricum temporar (doar validare pipeline, Faza 4 îl înlocuiește cu modelul propriu
  antrenat pe cutii) — alegerea de arhitectură de bază are sens făcută atunci, nu acum pe un model
  ce va fi oricum aruncat.

### Faza 4a — pipeline de embeddings, validare tehnică (implementat, testat live pe device — 2026-09-11)

**Context**: Faza 4 (Recunoaștere & enrollment) e mare — utilizatorul lucrează prima dată cu
embeddings/metric learning, discuție educațională purtată înainte de plan (ce e un embedding, de
ce metric learning nu clasificator, cum se antrenează — ArcFace/triplet loss, transfer learning de
la un backbone pretrained, fezabil pe GPU propriu în ore nu zile — și de unde vin datele: seed
colectat manual vs. acumulare organică din enrollment-ul userilor). **Decizie comună**: NU
antrenăm nimic acum (nu există încă dataset propriu de cutii, ca la Faza 3a) — Faza 4a validează
DOAR pipeline-ul tehnic (crop → embedding → comparare) cu un model generic pretrained, exact
disciplina de scop de la 3a-ii. **Fără galerie Room, fără enrollment, fără legătură Nomenclator/
Treatment** — vin în Faza 4b, când persistarea unor embeddings etichetate chiar are sens.

- **Model: MediaPipe `ImageEmbedder`** (`com.google.mediapipe:tasks-vision:1.0.0`, versiune
  stabilă, NU LiteRT brut ca la YOLO) — API de nivel înalt (ca ML Kit) care gestionează singur
  preprocesarea, evită o repetare a saga-i NCHW/proto-layout de la Faza 3a-ii/iii. Model implicit
  `mobilenet_v3_small.tflite` (MobileNetV3-Small, ImageNet, generic — NU antrenat pe cutii),
  **comis în repo** (`app/src/main/assets/mobilenet_v3_small_embedder.tflite`, ~3.9MB, descărcat
  de la URL-ul oficial Google Storage, confirmat prin `curl -I`).
- **API confirmat cu `javap` real** (nu doar grep pe bytecode ca la explorarea inițială — lecție
  nouă de tehnică: `javap` era deja instalat local, cale completă
  `C:\Program Files\Java\jdk-17\bin\javap.exe`, mult mai fiabil decât grep pe stringuri din
  `.class` pt. semnături exacte de metode): `ImageEmbedder.createFromOptions` →
  `embed(MPImage): ImageEmbedderResult` → `.embeddingResult().embeddings()[0].floatEmbedding()`
  întoarce direct `float[]` (NU `Optional<FloatEmbedding>` cum presupusese prima variantă de cod —
  bug de compilare prins imediat de compilator, nu unul silențios de runtime ca la NCHW, categorie
  de risc mult mai mică).
- **`domain/recognition/CosineSimilarity.kt`** (nou pachet — mirror `domain/vision`/`domain/gs1`):
  `cosineSimilarity(FloatArray, FloatArray): Float`, pur, gestionează vector-zero (evită NaN). **6
  teste noi**, toate trec. MediaPipe are propriul `ImageEmbedder.cosineSimilarity(Embedding,
  Embedding)` static, dar NU folosit — ar aduce tipul vendor `Embedding` în domain, încalcă
  regula de puritate a stratului `domain` (ca `AuthSessionState` pt. Supabase).
- **`ui/vision/ImageEmbedderModel.kt`** (nou, glue Android): încearcă GPU, fallback grațios la
  CPU — mirror EXACT `YoloSegModel.createModel()` (Faza 3a-iii), pattern deja confirmat. Pe acest
  device: GPU a reușit direct.
- **`ui/vision/VisionScanScreen.kt`** (extins, UI de validare temporară — va fi înlocuită de
  fluxul real de enrollment în Faza 4b): apasă lung pe ecran = îngheață embedding-ul celei mai
  mari detecții curente ca "referință" (`State` local, NU persistat); banner sus arată
  similaritatea live față de referință. Embedding calculat o singură dată per cadru (doar pt. cea
  mai mare detecție, nu toate) — cost redus.
- **Bug real găsit + fixat prin testare live pe device**: prima încercare a dat similaritate doar
  ~60% între două obiecte similare (2 banane) — utilizatorul a semnalat, corect, că pare scăzut.
  **Nu era un bug de implementare** — explicat utilizatorului: un backbone ImageNet antrenat prin
  clasificare (nu metric learning) nu e optimizat explicit ca aceeași categorie să dea cosine
  similarity mare, doar să distingă categorii — 60% e tipic, nu o eroare. **Îmbunătățire aplicată,
  la cererea utilizatorului**: crop-ul trimis la embedding folosește acum masca de segmentare
  reală (Faza 3a-iii, `Detection.mask`) ca să elimine fundalul (pixeli din afara măștii → negru
  opac), nu doar dreptunghiul brut de încadrare — reutilizare directă a investiției din 3a-iii.
  `cropToBox`/`applyMask` (funcții private noi în `VisionScanScreen.kt`, aceeași tehnică de
  "întinde grid-ul mic al măștii peste dreptunghiul final" ca în `DetectionOverlay.maskBitmap`,
  dar pt. selecție de pixeli, nu tentă vizuală translucidă). **Confirmat pe device**: similaritate
  vizibil mai mare la obiecte similare, discriminarea (obiecte diferite → similaritate mică) s-a
  păstrat.
- **Fără teste instrumentate noi** — glue Android/MediaPipe, netestabil semnificativ în JVM, ca
  restul Fazei 3a/4a.
- **Pe branch `feature/faza4a-embeddings-pipeline`**, de comis + PR (merge doar la cerere
  explicită, convenția stabilă).

---

## 8. CE URMEAZĂ — TODO

### 8a. Backlog (mic, neplanificat pe fază)
- **Switch limbă RO/EN** — cerut de utilizator (2026-09-07). Scaffolding deja pregătit
  (`locales_config.xml`, `android:localeConfig`, toate stringurile în `strings.xml`). Rămâne de
  făcut: `values-en/strings.xml` cu traduceri + `<locale android:name="en"/>` în
  `locales_config.xml` + un mecanism de selecție (ecran de setări nou, sau întrerupător simplu care
  apelează `AppCompatDelegate.setApplicationLocales(...)` / API-ul per-app language din Android 13+).
- **`LocalPatientProfileProvider` — un singur UUID local per instalare, nu per cont Supabase**
  (vezi „Bug-uri semnificative", punctul 6, secțiunea 7). Rar în producție (un pacient = propriul
  telefon), dar reapare la delogare + autentificare cu **alt cont Pacient existent** pe același
  telefon — RLS blochează corect (`42501`), dar userul vede o eroare brută de salvare la
  onboarding, nu un mesaj clar. Fix posibil: la conflict de tip owner mismatch pe upsert,
  regenerează UUID-ul local și reîncearcă automat.

### 8b. Roadmap faze următoare
- **Faza 1.5 — Conturi & Roluri:** ✅ **complet implementată, merge-uită pe `main` (PR #1-#8),
  testată live pe device fizic** — vezi secțiunea 7 pentru detalii complete pe sub-fază.
  **Următorul pas, la alegere:**
  - **Confirmare finală** că toate migrările `supabase/migrations/0003-0007*.sql` sunt rulate
    (Supabase Dashboard, în ordine, după 0001-0002) — testarea live a acoperit fluxul funcțional,
    dar nu verificat explicit migrare-cu-migrare.
  - **1.5c (sync propriu-zis al Pacientului)** rămâne neverificat separat pe device.
  - **Profil dependent** (pacient vârstnic fără cont propriu) — amânat explicit din 1.5d, cere
    suport multi-profil local în Room (schimbare majoră de arhitectură).
  - **1.5f — audit & consimțământ** (GDPR, ecran „Cine îmi vede datele") / **1.5g — teste RLS
    adversariale** — vezi `docs/user-management-plan.md` secțiunea 8, neatinse încă.
  - **Sau trecem direct la Faza 2** (identificare — Nomenclator ANMDMR + scanare) — Faza 1.5 e
    considerată suficient de matură funcțional, 1.5f/1.5g sunt hardening, nu blocante.
- **Faza 2a — Import Nomenclator + căutare/asociere:** ✅ **complet implementată, mergeuită pe
  `main` (PR #10)**, migrările `0008`/`0009` rulate — vezi secțiunea 7.
- **Faza 2b-i — Scanare GS1 DataMatrix + catalog `gtin_mappings`:** ✅ **complet implementată,
  mergeuită pe `main` (PR #11), migrările `0010`/`0011`/`0012` rulate, cont marcat contribuitor de
  încredere, push+pull pe catalogul comun + afișare expirare testate live pe device** — vezi
  secțiunea 7.
- **Faza 2b-ii — OCR fallback:** ❌ **implementată, testată live, apoi ELIMINATĂ** — rată de succes
  prea scăzută în practică (vezi secțiunea 7). Rămân doar scanare cod + introducere manuală
  (cu fallback fuzzy) ca metode de identificare la această etapă.
- **Faza 3a-i — CameraX feed live + permisiune:** ✅ **complet implementată, mergeuită pe `main`**
  (PR #12) — vezi secțiunea 7.
- **Faza 3a-ii — model LiteRT (YOLO-seg) + decodare cutii + overlay:** ✅ **implementată, testată
  live pe device, confirmată funcțională, mergeuită pe `main` (PR #13)** (bug real NCHW vs. NHWC
  găsit + fixat — vezi secțiunea 7).
- **Faza 3a-iii — măști de segmentare (contur real) + accelerator GPU:** ✅ **implementată, testată
  live pe device, confirmată funcțională, mergeuită pe `main` (PR #14)** — măștile funcționale din
  prima încercare + accelerare GPU ~5x (rezolvă lag-ul de overlay semnalat) — vezi secțiunea 7.
- **Faza 3 (restul) — Viziune:** tracking multi-obiect pe cadru de ansamblu cu modelul de
  recunoaștere propriu (după Faza 4 — embeddings), nu doar model generic COCO.
- **Faza 4a — pipeline de embeddings (validare tehnică):** ✅ **implementată, testată live pe
  device, confirmată funcțională** (MediaPipe ImageEmbedder + crop mascat cu segmentarea din
  3a-iii, semnal confirmat pe ambele direcții) — vezi secțiunea 7. Pe branch
  `feature/faza4a-embeddings-pipeline`, de mergeuit la cerere explicită.
- **Faza 4 (restul) — Recunoaștere & enrollment:** galerie Room (embeddings + legătură
  Nomenclator), enrollment multi-view + top-k candidați (Faza 4b), integrare recunoaștere runtime
  + **colorare contur** după statusul dozei (Faza 4c) — vezi decizia de strategie date/antrenare
  în secțiunea 7 (Faza 4a): fără antrenare proprie acum, date acumulate organic din enrollment.
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
- **Git de pe mașina locală** (Windows): repo privat `github.com/bolosandrei/PillPronto`, branch `main`. Commit pe feature + push, PR + merge, șterge branch-ul (local + `origin`) după merge.
  - Notă: NU rula git din medii care nu-și pot curăța fișierele `.lock` (ex. sandbox Cowork) — lasă `.git/index.lock` blocant.
- **Testare pe device fizic**: workflow standard pentru orice feature UI/backend nou — `adb install -r` + testare manuală ghidată de utilizator, `adb logcat` pentru diagnosticare la eșec. Pattern confirmat repetat în acest proiect: testarea pe device găsește constant bug-uri reale (RLS, curse UI) pe care code review-ul singur nu le prinde — vezi secțiunea 7, „Bug-uri semnificative".
- Subagenți utili: research (surse), verificare (citări/teste), code-review la PR.
