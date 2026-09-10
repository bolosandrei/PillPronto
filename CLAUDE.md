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
  (fix), `10` lot (variabil ≤20), `21` serial (variabil ≤20), cele 4 mandatate FMD.
  - **Bug real găsit + fixat la testarea pe device**: primul test a eșuat — codul era corect
    DataMatrix (`format=16`), dar payload-ul are un caracter FNC1/GS literal (`0x1D`) **înaintea**
    primului AI (Play Services Code Scanner nu-l elimină el însuși), pe care parserul nu-l
    anticipa. Fix: `Gs1Parser.parse()` elimină un eventual prefix GS înainte de a parsa. Confirmat
    cu 2 payload-uri reale capturate prin logging temporar (`adb logcat`) de pe cutii fizice,
    păstrate ca teste de regresie în `Gs1ParserTest` (GTIN e dată de produs public, nu personală).
    Retestat pe device după fix — **confirmat funcțional** de utilizator (scan → recunoaștere OK).
- **`ui/gtinmapping/AssociateGtinScreen`+`ViewModel`** — ecran dedicat (buton „Asociere coduri
  (GTIN)" în tab-ul Cont, vizibil indiferent de autentificare): scan → caută în Nomenclator →
  alege → salvat → gata pt. următorul, **fără să creeze un tratament** (spre deosebire de fluxul
  din `AddTreatmentScreen`). `NomenclatureSuggestions` extras din `AddTreatmentScreen.kt` în
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
- **Faza 2b-ii** (următorul pas, separat intenționat): OCR ca fallback pt. cutii fără cod lizibil —
  vezi secțiunea 8b.
- **De făcut sesiunea viitoare**: userul rulează manual migrările `0010` și `0011` (în ordine), se
  marchează contribuitor de încredere (SQL direct: `update profiles set is_trusted_contributor =
  true where id = '<uid>'`), testează pe device confirmarea unei mapări + verifică apariția
  rândului în `gtin_mappings` (Supabase Table Editor) + verifică pull-ul pe alt cont/device. Apoi
  commit + push + PR + merge.

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
- **Faza 2b-i — Scanare GS1 DataMatrix + catalog `gtin_mappings`:** ✅ **implementată, testată
  funcțional pe device** (bug de parser găsit + fixat live), **necomisă încă** (branch
  `feature/faza2b-i-gs1-scan`) — de rulat manual migrările `0010`+`0011` (în ordine), de marcat
  userul contribuitor de încredere, de testat push+pull către catalogul partajat, apoi
  commit+push+PR+merge — vezi secțiunea 7 pentru detalii complete.
- **Faza 2b-ii — OCR fallback** (următorul pas, după 2b-i): OCR (ML Kit Text Recognition) pt.
  cutii fără cod lizibil/DataMatrix absent — alimentează câmpul de căutare Nomenclator existent cu
  textul recunoscut, nu duplică UI-ul de sugestii.
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
- **Git de pe mașina locală** (Windows): repo privat `github.com/bolosandrei/PillPronto`, branch `main`. Commit pe feature + push, PR + merge, șterge branch-ul (local + `origin`) după merge.
  - Notă: NU rula git din medii care nu-și pot curăța fișierele `.lock` (ex. sandbox Cowork) — lasă `.git/index.lock` blocant.
- **Testare pe device fizic**: workflow standard pentru orice feature UI/backend nou — `adb install -r` + testare manuală ghidată de utilizator, `adb logcat` pentru diagnosticare la eșec. Pattern confirmat repetat în acest proiect: testarea pe device găsește constant bug-uri reale (RLS, curse UI) pe care code review-ul singur nu le prinde — vezi secțiunea 7, „Bug-uri semnificative".
- Subagenți utili: research (surse), verificare (citări/teste), code-review la PR.
