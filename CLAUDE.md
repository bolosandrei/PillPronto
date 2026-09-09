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
  Instrumentate (`app/src/androidTest/`): Room DAO pe bază in-memory + un test Compose de fum
  (navigare bottom bar) via `@HiltAndroidTest`; rulează pe emulator/dispozitiv, nu din CLI fără device.
  `testInstrumentationRunner` = `com.pillpronto.HiltTestRunner` (instanțiază `HiltTestApplication`).
- **Stringuri:** toate textele afișate utilizatorului sunt în `res/values/strings.xml` (RO, fără
  calificator de limbă) — nu se hardcodează text în Compose. Erorile de validare din ViewModels
  (ex. `AddTreatmentError`) sunt enum-uri tipizate, nu String — Composable-ul mapează la
  `stringResource(...)`, ca ViewModel-ul să rămână fără dependență de Context Android.
  `AndroidManifest.xml` are `android:localeConfig="@xml/locales_config"` (scaffolding pt. switch
  RO/EN viitor — vezi secțiunea 8).

## 5. Comenzi build & test

```bash
./gradlew assembleDebug              # build APK debug
./gradlew testDebugUnitTest          # teste unitare (PDC/MPR + mappers)
./gradlew connectedDebugAndroidTest  # teste instrumentate (Room DAO + smoke test Compose) — necesită device/emulator
./gradlew installDebug               # instalare pe dispozitiv/emulator conectat
./gradlew lint                       # lint
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

### Faza 1 — MVP aderență (complet, inclusiv toate rafinările)
- **Introducere tratament** (manual): nume, dozaj, ore (chips), dată start/end — cu **TimePicker/DatePicker** și validare; tratamente **„la nevoie" (PRN)**, fără orar fix.
- **Editare & ștergere** tratament (tap pe card → editare; ștergere cu confirmare, **swipe-to-delete** în listă). Istoricul dozelor luate/ratate se păstrează la editare.
- **Ecran de detaliu tratament** (`TreatmentDetailScreen`) cu istoric de administrare per medicament.
- **Persistență** Room (offline-first): `treatments`, `dose_logs` (FK cascade), schema v2 (PRN).
- **Generarea dozelor** din orar (sare peste orele deja trecute).
- **Ecranul „Azi"**: strip de zile scopat pe **luna afișată** (derivată din `selectedDate`, nu stare separată) + rând **lună/an** cu săgeți (`onPreviousMonth`/`onNextMonth`, clamping automat via `LocalDate.plusMonths/minusMonths`) și eticheta lunii deschide picker-ul nativ M3 (`DatePickerDialogBox`, extras în `core/ui/components/` și reutilizat și la formularul de tratament) pentru salt direct pe orice an.
- **Remindere** ca alarme **exacte** per-doză (`AlarmManager`).
- **Notificări** cu: **tap → deschide app și navighează direct pe tab-ul „Azi"** (deep-link prin `MainActivity.onNewIntent` + `EXTRA_OPEN_TODAY`, funcționează și cu app-ul deja deschis pe alt tab); butoane **„Confirmă" / „Omite"** (marchează doza direct, via `DoseActionReceiver`, fără a deschide app-ul).
- **WorkManager periodic (6h):** marchează dozele depășite ca `MISSED`, **extinde orizontul** de doze (fereastră rulantă), resincronizează alarmele.
- **Metrici de aderență:** `ComputeAdherenceUseCase` calculează **PDC** (zile acoperite / total) și **MPR** (doze luate / programate); ecran „Aderență" cu prag 0.80.
- **Permisiuni** runtime (notificări) + banner alarme exacte.
- **Icon launcher propriu** (adaptive icon vectorial, capsulă în culorile temei) + `dataExtractionRules` (Android 12+, exclude `pillpronto.db` de la backup cloud/transfer, consecvent cu `backup_rules.xml`).
- **Localizare:** toate stringurile în `res/values/strings.xml` (RO); `AddTreatmentError` enum tipizat în loc de text brut în ViewModel; `locales_config.xml` + `android:localeConfig` — scaffolding pentru switch RO/EN (vezi backlog, secțiunea 8).
- **Teste unitare:** `ComputeAdherenceUseCaseTest` (3 scenarii PDC/MPR), `MappersTest` (round-trip), `FakeDoseRepository`.
- **Teste instrumentate:** `TreatmentDaoTest`, `DoseDaoTest` (Room in-memory, inclusiv cascade delete), `NavigationSmokeTest` (Compose, navigare bottom bar) — `HiltTestRunner` configurat.

Use-cases existente: `AddTreatmentUseCase`, `EditTreatmentUseCase`, `DeleteTreatmentUseCase`, `GetTreatmentUseCase`, `GenerateDosesUseCase`, `ObserveTreatmentsUseCase`, `ObserveTodayDosesUseCase`, `ObserveTreatmentHistoryUseCase`, `ObserveActiveAsNeededTreatmentsUseCase`, `LogDoseUseCase`, `LogAsNeededDoseUseCase`, `ComputeAdherenceUseCase`, `MarkOverdueDosesUseCase`, `ExtendDoseHorizonUseCase`.

### Faza 1.5a+1.5b — Conturi & autentificare (parțial — vezi `docs/user-management-plan.md`)
- `TreatmentEntity`/`DoseLogEntity` au `patientProfileId: String` — identitate locală generată o
  singură dată (`LocalPatientProfileProvider`, UUID persistat în `SharedPreferences`), devine
  `patient_profiles.id` în Supabase la onboarding (client-generated UUID, fără reconciliere).
  `PillProntoDatabase` la `version = 3`.
- Schema Postgres + RLS **scrise și executate** de utilizator: `supabase/migrations/0001_init_schema.sql`,
  `0002_rls_policies.sql` (`profiles`, `patient_profiles`, `links`, `treatments`, `dose_logs`, `audit_log`).
- SDK Supabase Kotlin conectat (`core/di/SupabaseModule.kt`, BOM `3.5.0`, module `auth-kt` +
  `postgrest-kt`) — credențiale din `local.properties` → `BuildConfig` (`SUPABASE_URL`,
  `SUPABASE_PUBLISHABLE_KEY`), niciodată în cod. `compileSdk` ridicat la **36** (`targetSdk`
  rămâne 35) — `androidx.browser`, adus tranzitiv de `auth-kt`, cere minim compileSdk 36.
- Autentificare email/parolă completă: `AuthRepository`/`ProfileRepository` + use-cases
  (`SignUpUseCase`, `SignInUseCase`, `SignOutUseCase`, `ObserveAuthSessionUseCase`,
  `GetProfileUseCase`, `CompleteOnboardingUseCase`). `AuthSessionState` traduce `SessionStatus`
  din SDK — domeniul nu depinde de vendor.
- Tab nou **„Cont"** (al 4-lea, bottom bar) — `ui/account/AccountScreen.kt` (toggle
  autentificare/înregistrare) + `ui/onboarding/OnboardingScreen.kt` (alegere rol, o singură dată
  după primul cont). **Cont opțional** — aplicația rămâne 100% funcțională fără login.
- **Verificat pe device cu cont real** (2026-09-07) — 3 bug-uri găsite și rezolvate, cel mai
  notabil: `AccountViewModel` nu refăcea profilul după onboarding reușit (doar la schimbarea
  sesiunii), retrimițând userul la nesfârșit pe onboarding gol deși contul chiar fusese creat.
  Fix + detalii complete: `docs/user-management-plan.md` secțiunea 8, sub-punctul 1.5b.
- **NU e încă implementat:** Google Sign-In (necesită 2 OAuth Client ID Google Cloud + SHA-1,
  blocaj extern separat), fluxurile Aparținător/Medic/Farmacist (1.5d-1.5g).

### Faza 1.5c — Sync layer Room↔Supabase (implementat — 2026-09-09)
- Sincronizare bidirecțională a **propriilor** date ale Pacientului (treatments + dose_logs cu
  status final) — Room rămâne sursa de adevăr locală, Supabase e strat opțional. Vezi
  `docs/user-management-plan.md` secțiunea 4/8 pentru design complet.
- **Dozele PENDING nu se sincronizează** (stare de programare locală, nu istoric de aderență) —
  doar `TAKEN`/`MISSED`/`SKIPPED`. Elimină nevoia de tombstone-uri pentru `deleteFuturePending`.
- **Outbox pattern:** `TreatmentEntity`/`DoseLogEntity` au acum `remoteId`/`updatedAt`/`dirty`
  (`PillProntoDatabase` la `version = 4`); tabel nou `pending_remote_deletes` pentru ștergeri de
  propagat. Conflict resolution: **last-write-wins pe `updatedAt`**, un rând local `dirty`
  (modificare nepush-uită) nu e niciodată suprascris de un pull.
- **`data/sync/SyncManager.kt`** (coordonator, precedent `ReminderCoordinator`) — ordine
  obligatorie: delete-uri → push treatments → push dose_logs → pull treatments → pull dose_logs →
  regenerare doze PENDING (`GenerateDosesUseCase`) pentru tratamentele nou-scrise din pull →
  resincronizare alarme. `data/sync/SyncRemoteDataSource.kt`/`SupabaseSyncDataSource.kt` —
  abstractizare peste Postgrest, doar pentru testabilitate.
- **`data/work/SyncWorker.kt`** — programat periodic (30 min, `MaintenanceScheduler`) + o dată la
  pornire (`PillProntoApp.onCreate()`, `scheduleSyncOnStartup`, implementează „pull la pornire"
  cerut în plan). No-op sigur dacă userul nu e autentificat ca Pacient.
- **Seam-uri de testabilitate introduse** (fiecare cu un motiv concret, nu abstractizare
  gratuită): `ReminderSync` (peste `ReminderCoordinator` — constructorul `ReminderScheduler` atinge
  `AlarmManager`/`Context` real, netestabil în JVM) și `PatientProfileIdProvider` (peste
  `LocalPatientProfileProvider` — constructorul atinge `SharedPreferences`/`Context` real; mutată
  în `domain/repository/` la 1.5d, vezi mai jos, ca ViewModels să o poată injecta prin use-case-uri
  fără să încalce layering-ul domain/data). Restul consumatorilor existenți (ViewModels,
  use-cases, `BootReceiver`, `AdherenceMaintenanceWorker`) continuă să injecteze clasele concrete,
  neschimbate.
- **Bug găsit în plan review, fixat înainte de implementare:** `markOverdueMissed` (DAO) nu seta
  `dirty`/`updatedAt` la tranziția PENDING→MISSED — fără fix, dozele ratate (cel mai important
  semnal de aderență) nu s-ar fi sincronizat niciodată.
- **Teste unitare:** `SyncManagerTest` (11 cazuri — no-op neautentificat/rol greșit, push doar
  dirty + clear după succes, push eșuat nu blochează ciclul, pull nu suprascrie dirty local, LWW
  în ambele sensuri, insert din pull regenerează dozele + reprogramează alarme, tombstone consumat/
  păstrat după succes/eșec) cu fake-uri noi în `util/`: `FakeTreatmentDao`, `FakeDoseDao`,
  `FakePendingRemoteDeleteDao`, `FakeSyncRemoteDataSource`, `FakeReminderSync`,
  `FakePatientProfileIdProvider`.
- **NU verificat încă pe device fizic** (Supabase Dashboard) — sync-ul e feature de fundal fără UI
  propriu; verificarea manuală descrisă în planul de implementare (creare/editare/ștergere
  tratament + confirmare doză → apariția/dispariția rândurilor remote) rămâne de făcut.

### Faza 1.5d — Flux Aparținător, viewer (implementat — 2026-09-09)
- Un Aparținător se leagă de un Pacient **care are deja cont și telefon propriu** și îi vede
  tratamentele + aderența, read-only, plus notificare la doză ratată. „Profil dependent" (pacient
  vârstnic fără cont propriu) **amânat** — ar cere suport multi-profil local în Room, schimbare
  majoră separată. Detalii complete: `docs/user-management-plan.md` secțiunea 8 (1.5d).
- **Migrare `supabase/migrations/0003_links_open_invite.sql`** — de rulat manual de utilizator în
  Supabase Dashboard, ca 0001/0002. Conține:
  - `grantee_user_id` pe `links` devine nullable (invitația se creează înainte să se știe cine o
    revendică).
  - Funcție `claim_link(p_invite_code text) SECURITY DEFINER` — **decizie de securitate găsită în
    plan review, nu în cererea inițială**: o politică RLS simplă de UPDATE pentru „claim" nu poate
    funcționa corect (UPDATE cu WHERE cere vizibilitate SELECT separată pe rândul țintă; o
    politică de SELECT „toate rândurile pending nerevendicate" ar scurge toate codurile de
    invitație active, oricui autentificat — enumerare). Funcția ocolește RLS intern, acceptă doar
    codul ca parametru, hardcodează exact ce coloane se modifică.
  - Trigger `links_guard_update` — hardening suplimentar: închide o gaură preexistentă din
    politica `links_grantee_respond` (1.5a), care nu împiedica un grantee să-și schimbe propriul
    rând `links` către alt `patient_profile_id`/`role` în același UPDATE.
- **Migrare `supabase/migrations/0004_fix_links_rls_recursion.sql`** — **bug real găsit la
  testarea pe device** (2026-09-09, primul test manual al 1.5d): `ManageAccessScreen` → „Generează
  cod nou" întorcea eroare Postgres `infinite recursion detected in policy for relation "links"`
  (cod `42P17`). Cauză, prezentă încă din 1.5a (`0002_rls_policies.sql`), nedescoperită pentru că
  nimic nu interogase direct `links`/`treatments`/`dose_logs` până la 1.5d: `links_owner_manage`
  face subquery pe `patient_profiles`, iar `patient_profiles_linked_read` face subquery invers pe
  `links` — RLS se reevaluează tranzitiv la fiecare acces la tabel, deci evaluarea uneia declanșează
  evaluarea celeilalte, la nesfârșit. **Același tipar există și între `treatments`/`links` și
  `dose_logs`/`treatments`/`patient_profiles`/`links`** — ar fi blocat probabil și sync-ul din
  1.5c, nedescoperit din același motiv (1.5c nu e verificat încă pe device). Fix: funcții
  `SECURITY DEFINER` (`is_patient_profile_owner`, `has_accepted_link`, `is_treatment_owner`,
  `has_accepted_link_for_treatment`) care ocolesc RLS intern, înlocuind subquery-urile corelate
  directe din politici — pattern-ul standard Postgres/Supabase pentru acest caz. **De rulat manual
  de utilizator, după 0003.**
- **Cod de invitație**: text simplu, 8 caractere alfanumerice (fără 0/O/1/I), generat client-side
  (`SecureRandom`), distribuit prin Android share sheet (`Intent.ACTION_SEND`) — fără QR vizual în
  v1 (fără dependență nouă).
- **Notificare doză ratată**: worker periodic (`CaregiverAlertWorker`, 30 min, alături de
  `SyncWorker`), no-op dacă rolul curent != CAREGIVER. Deduplicare pe mulțime de `remoteId`
  (`MissedDoseChecker`, clasă pură testabilă — nu pe timestamp, un status MISSED e terminal).
  Canal de notificare separat (`caregiver_alerts`) de `ReminderScheduler` (acela e specific
  remindere proprii cu acțiuni Confirmă/Omite).
- **Fetch date pacient legat**: direct din Supabase (`LinkedPatientDataRepository`), niciodată din
  Room local. Filtrare server-side pe `treatment_id` (Postgrest `.isIn(...)`) — evită over-fetch-ul
  dozelor altor pacienți legați ai aceluiași Apartinător.
- **`AdherenceCalculator`** extras din `ComputeAdherenceUseCase` (formula PDC/MPR pură,
  `compute(logs): AdherenceStats`) — reutilizat și de `GetLinkedPatientAdherenceUseCase` (loguri
  remote). `ComputeAdherenceUseCase` rămâne wrapper subțire, semnătură publică neschimbată.
- **UI**: fără tab nou în bottom bar — buton „Gestionează accesul"/„Pacienții mei" în
  `AccountScreen`, condiționat de rol. Ecrane noi: `ui/access/ManageAccessScreen` (Pacient),
  `ui/patients/MyPatientsScreen` + `PatientDetailScreen` (Apartinător, read-only, fără buton
  editare — stil `TreatmentDetailScreen`).
- **Teste unitare**: `AdherenceCalculatorTest`, `MissedDoseCheckerTest`,
  `GetLinkedPatientAdherenceUseCaseTest`, `ManageAccessViewModelTest`, `MyPatientsViewModelTest`,
  `PatientDetailViewModelTest` — 21 cazuri noi, toate trec. Fake-uri noi: `FakeLinkRepository`,
  `FakeLinkedPatientDataRepository`.
- **Testat parțial pe device fizic** (2026-09-09) — primul test manual (generare cod) a scos la
  iveală bug-ul de recursivitate RLS de mai sus (`0004_fix_links_rls_recursion.sql`). Migrările
  0003+0004 trebuie rulate de utilizator (în această ordine) înainte ca fluxul complet (invitație
  → claim → vizibilitate → notificare) să funcționeze end-to-end; planul de verificare manuală
  (inclusiv teste adversariale pe RLS) e în `docs/user-management-plan.md` secțiunea 8.
- **Al doilea bug găsit la testare pe device** (2026-09-09, cont Apartinător nou): după onboarding
  reușit (rol + nume + Continuă), userul era retrimis direct înapoi pe ecranul „Ce fel de cont
  ai?" — nu un bug nou de 1.5d, ci o condiție de cursă rămasă în fix-ul din 1.5b
  (`AccountViewModel.refreshProfile`). `refresh()` (apelat de `AccountScreen` la
  `ON_RESUME`, după ce onboarding-ul face `popBackStack()`) pornea fetch-ul de profil async **fără**
  să marcheze mai întâi "verificare în curs" — recompunerea imediată a `AccountScreen` vedea starea
  veche (`profileChecked=true`, `profile=null`, rămasă de dinainte de onboarding) și sărea înapoi
  pe onboarding prin `LaunchedEffect`, înainte ca fetch-ul proaspăt să apuce să răspundă. Fix:
  `refreshProfile` setează sincron `profileChecked=false` chiar înainte de a porni fetch-ul, deci
  fereastra de recompunere vede „se verifică", nu „lipsă profil" — `AccountScreen` arată scurt
  `LoadingIndicator` în loc să navigheze greșit. Test nou: `AccountViewModelTest` (`refresh dupa
  onboarding reface profilul...`).
- **Confirmat funcțional end-to-end pe device** (2026-09-09, după cele două fix-uri de mai sus) —
  cod generat de Pacient → introdus manual de Aparținător → apare în „Pacienții mei". Utilizatorul
  a cerut apoi 3 îmbunătățiri UX pe baza testării reale (vezi rafinarea de mai jos).
- **Rafinare UX — fricțiune redusă la legare + nume Aparținător vizibil** (2026-09-09, mai multe
  iterații pe baza testării live a utilizatorului):
  - **Deep link `pillpronto://invite?code=XXXX`** (schemă proprie, fără domeniu/App Links) —
    pattern identic cu `openTodayRequests` (tap pe notificare reminder): `MainActivity` emite
    printr-un `MutableSharedFlow`, `PillProntoNavHost` navighează la „Pacienții mei" cu codul
    pre-completat (userul tot apasă „Adaugă pacient" — confirmare păstrată, nu claim automat
    silențios). Parsare/construcție centralizate în `ui/access/InviteLink.kt`
    (`buildInviteUri`/`extractInviteCode`) — pe `String`, nu pe `android.net.Uri` (stub în teste
    JVM fără Robolectric), ca să rămână testabil.
  - **Link-ul text s-a confirmat pe device necliclabil în WhatsApp** (auto-linkify doar pe
    `http(s)://`, nu pe scheme proprii — semnalat înainte de implementare, confirmat de user după
    3 încercări de reformatare). **Eliminat complet din textul distribuit** — rămâne doar codul +
    mențiunea codului QR. Codul QR (`com.google.zxing:core`, doar generare) e mecanismul „fără
    tastare" funcțional: **atașat ca imagine reală** în share sheet (nu doar codat în text) via
    `FileProvider` (`res/xml/file_paths.xml`, PNG temporar în `cache/shared_images/`,
    `Intent.ACTION_SEND` cu `type=image/png` + `EXTRA_STREAM`) — `file://` direct ar arunca
    `FileUriExposedException` pe Android 7+.
  - **Buton de scanare QR** pe „Pacienții mei" (Apartinător) — `com.google.android.gms:play-services-code-scanner`
    (`GmsBarcodeScanning`), NU CameraX/ML Kit manual: modulul gestionează integral UI-ul de
    cameră + permisiunea, fără `CAMERA` în manifest. **Nu e începutul Fazei 2** (aceea ramane
    CameraX + ML Kit pentru detecție multi-obiect pe cutii de medicamente) — aici doar citește
    textul unui singur cod QR, reutilizând `extractInviteCode` din același `InviteLink.kt`.
  - **Numele Aparținătorului vizibil Pacientului** — migrare nouă
    `supabase/migrations/0005_profiles_visible_to_linked_grantee.sql` (funcție `is_linked_grantee`
    `SECURITY DEFINER`, aceeași tehnică ca 0004, deși aici niciun tabel nu subqueria `profiles`
    azi — păstrat consecvent). `LinkRepository.getMyCaregivers` (pattern identic `getMyPatients`,
    două query-uri) + `GetMyCaregiversUseCase`; `ManageAccessScreen` arată „Acces acordat lui
    <nume>" în loc de textul generic pe legăturile `ACCEPTED`.
  - Buton `AccountScreen`: „Gestionează accesul" → „Gestionează accesul Aparținătorilor". Buton
    nou „Anulează" pe invitațiile `PENDING` (reutilizează `revokeLink` existent).
  - Teste noi: `InviteLinkTest` (6), + cazuri noi în `ManageAccessViewModelTest`/
    `MyPatientsViewModelTest` (potrivire nume Aparținător, prefill din `SavedStateHandle`).
  - **Neverificat încă pe device**: doar migrarea 0005 (SQL, de rulat de utilizator după
    0001-0004) — restul (deep link, QR ca imagine, scanare, nume Aparținător) verificat live pe
    device fizic în timpul dezvoltării.
  - **Bug real găsit la testarea scanării QR** (2026-09-09): revendicarea unui cod nou pentru un
    Apartinator cu care Pacientul mai avusese o legătură (chiar revocată) eșua cu eroare brută
    Postgres „duplicate key... links_patient_profile_id_grantee_user_id_key" (23505). Cauză:
    `unique(patient_profile_id, grantee_user_id)` din 0001 e globală, se aplică și rândurilor
    `revoked` — o reinvitare colidează cu istoricul revocat. Fix:
    `supabase/migrations/0006_fix_links_reinvite_constraint.sql` — constrângerea devine index
    unic parțial (`where status <> 'revoked'`), plus mesaj de eroare mai clar în `claim_link`
    pentru cazul legitim rămas (a doua legătură activă simultan). **De rulat manual, după 0005.**

### Faza 1.5e — Flux Medic/Farmacist, read-only (implementat — 2026-09-09)
- Analog cu 1.5d (Aparținător) — infrastructura de bază (RLS, `MyPatientsScreen`/
  `PatientDetailScreen`/use-case-urile de citire) era deja **agnostică la rol**, reutilizată
  integral, zero schimbări. `CaregiverAlertWorker` rămâne strict pentru CAREGIVER (notificare doză
  ratată) — Medic/Farmacist nu primesc notificări, conform `docs/user-management-plan.md`
  secțiunea 6.
- **Pacientul alege rolul la generarea codului** (decizie confirmată cu utilizatorul) —
  `LinkRepository.createInvite`/`CreateInviteUseCase` capătă parametru `role: LinkRole =
  CAREGIVER_VIEWER` (default păstrat, niciun call-site existent nu s-a schimbat).
  `supabase/migrations/0007_professional_invite_role_check.sql` rescrie `claim_link` să valideze
  că rolul contului care revendică (citit din `profiles.role`/`clinician_type`) se potrivește cu
  rolul declarat al invitației — altfel eroare clară, nu confuzie tăcută. **De rulat manual, după
  0006.**
- **Ecran separat** pentru Pacient (decizie confirmată, nu unificat cu Aparținătorii):
  `ManageProfessionalAccessScreen`/`ManageProfessionalAccessViewModel` — selector de rol (`FilterChip`
  Medic/Farmacist, reutilizează `account_role_doctor`/`account_role_pharmacist`), altfel aceeași
  structură ca `ManageAccessScreen`. Ambele ecrane filtrează acum `links` client-side pe rol (tabela
  poate conține rânduri mixte) — `LinkRow`/`InviteQrCode`/`shareInviteCode`/`linkStatusLabel`/
  `manageAccessErrorMessage` extrase în `ui/access/AccessLinkComponents.kt` ca să nu se dubleze
  între cele două ecrane.
- **Flag „neverificat"** (auto-declarare, fără validare CUIM — decizie deja luată în
  `docs/user-management-plan.md` secțiunea 9) vizibil în **ambele** locuri cerute: pe lista
  Pacientului (`LinkRow(unverified = true)` pe `ManageProfessionalAccessScreen`) și pe propriul
  `AccountScreen` al Medicului/Farmacistului (`account_unverified_badge`).
- `AccountScreen.LoggedInView`: Pacientul are acum **două** butoane („Gestionează accesul
  Aparținătorilor" + „Gestionează accesul Medic/Farmacist"); CAREGIVER/DOCTOR/PHARMACIST rutează
  toate spre același buton „Pacienții mei" (`onMyPatients`), fără ecran separat pt. partea de
  citire.
- Teste noi: `ManageProfessionalAccessViewModelTest` (7 cazuri) + caz nou în
  `ManageAccessViewModelTest` (filtrare rol), toate trec.
- **Neverificat încă pe device** — migrarea 0007 trebuie rulată de utilizator (după 0001-0006).
- **Bug recurent găsit la testarea pe device a unui cont Medic nou** (2026-09-09): fix-ul de
  cursă din 1.5d (mai sus) era incomplet — userul tot era retrimis pe onboarding după succes
  (uneori de mai multe ori la rând, cerea mai multe Back-uri). Cauză reală: `LifecycleEventEffect(
  ON_RESUME)` din `AccountScreen` declanșează `vm.refresh()` printr-un callback de Lifecycle cu
  **timing incert** față de `LaunchedEffect`-ul de verificare din aceeași compoziție — uneori
  verificarea rula înaintea refresh-ului, citind starea veche. Fix definitiv: adăugat
  `LaunchedEffect(Unit) { vm.refresh() }` în `AccountScreen.kt`, declarat **înaintea**
  efectului de verificare (garantează ordine sincronă în aceeași trecere de compoziție);
  `LifecycleEventEffect(ON_RESUME)` păstrat separat pentru revenirea reală din fundal (unde
  compoziția nu se reface). Plasă de siguranță suplimentară: `launchSingleTop = true` pe
  `navController.navigate(Route.Onboarding.path)` în `PillProntoNavHost.kt`, ca eventuale
  regresii viitoare să nu mai stivuiască mai multe instanțe de Onboarding.
- **Navigare — buton de back pe toate ecranele secundare** (2026-09-09, cerut de utilizator după
  ce a semnalat bug-ul de mai sus): `BackTopAppBar` nou (`core/ui/components/BackTopAppBar.kt`,
  `TopAppBar` + `IconButton` cu `Icons.AutoMirrored.Filled.ArrowBack`), adăugat pe toate cele 7
  ecrane secundare (nu sunt în bara de jos): Onboarding, Gestionează accesul (Aparținători +
  Medic/Farmacist), Pacienții mei, Detaliu pacient, Adăugare/editare tratament, Detaliu tratament.
  Fiecare ecran capătă parametru nou `onBack: () -> Unit`, legat în `PillProntoNavHost.kt` la
  `navController.popBackStack()`. Gestul/butonul de sistem de back funcționau deja, dar o săgeată
  vizibilă e recomandarea Material Design curentă pentru discoverability — relevant mai ales aici,
  unde publicul țintă include pacienți vârstnici. Compilare + teste unitare + build APK debug +
  instalare pe device confirmate; testare manuală pe device încă neconfirmată de utilizator.
- **Testat pe device de utilizator** (2026-09-09) — navigarea cu butoane de back funcționează.
  A semnalat un efect secundar: tab-ul „Cont" arăta un „flash" de reîncărcare (spinner) de fiecare
  dată când revenea pe el, chiar dacă profilul era deja cunoscut — cauzat de `refresh()` (apelat
  la fiecare intrare în compoziție, vezi mai sus) care resetează sincron `profileChecked=false`,
  iar `AccountScreen` cerea `profileChecked && profile != null` ca să arate `LoggedInView`, deci
  orice refresh (chiar reușit din prima) trecea vizibil prin `LoadingIndicator`. Fix: condiția de
  randare devine doar `profile != null` — profilul cunoscut (chiar "stale" cât timp refresh-ul
  rulează tăcut pe fundal) rămâne afișat neîntrerupt; spinner-ul apare doar la primul fetch real
  (`profile == null`), nu la fiecare revenire pe tab. Logica de `profileChecked` pentru declanșarea
  onboarding-ului (`LaunchedEffect(state.sessionState, state.profileChecked, state.profile)`)
  rămâne neschimbată. Compilare + teste unitare + build APK + instalare pe device confirmate.

---

## 8. CE URMEAZĂ — TODO

### 8a. Backlog (mic, neplanificat pe fază)
- **Switch limbă RO/EN** — cerut de utilizator (2026-09-07). Scaffolding deja pregătit
  (`locales_config.xml`, `android:localeConfig`, toate stringurile în `strings.xml`). Rămâne de
  făcut: `values-en/strings.xml` cu traduceri + `<locale android:name="en"/>` în
  `locales_config.xml` + un mecanism de selecție (ecran de setări nou, sau întrerupător simplu care
  apelează `AppCompatDelegate.setApplicationLocales(...)` / API-ul per-app language din Android 13+).

### 8b. Roadmap faze următoare
- **Faza 1.5 — Conturi & Roluri (Pacient/Aparținător/Medic/Farmacist):** **1.5a + 1.5b + 1.5c +
  1.5d (+ rafinare UX) + 1.5e implementate**, 1.5a-1.5d mergeuite pe `main` (PR #1-#4), 1.5e pe
  branch `feature/1.5e-medic-pharmacist` (vezi secțiunea 7 mai sus) — schema + RLS + migrare Room,
  SDK Supabase + autentificare email/parolă + onboarding rol, sync layer Room↔Supabase, legătură
  Pacient↔Aparținător/Medic/Farmacist read-only (cod + QR + scanare + deep link + nume/rol vizibil
  + revocare/reinvitare + selecție rol la generare) + notificare doză ratată (doar CAREGIVER).
  **Următorul pas, la alegere:**
  - **Confirmare finală de la utilizator** că toate migrările `supabase/migrations/0003-0007*.sql`
    sunt rulate (Supabase Dashboard, în ordine, după 0001-0002) și fluxul complet funcționează
    end-to-end (invitație pt. fiecare rol → claim cu validare de rol → vizibilitate → notificare →
    revocare → reinvitare) — 1.5d testat live pe device, 1.5e nu încă.
  - **1.5c (sync propriu-zis al Pacientului)** rămâne neverificat separat pe device — posibil
    exercitat implicit prin testarea 1.5d, dar nu confirmat explicit.
  - **Google Sign-In** (completare 1.5b) — necesită acțiune manuală a utilizatorului mai întâi:
    2 OAuth Client ID-uri în Google Cloud Console (Web + Android, acesta din urmă cu amprenta
    SHA-1 a certificatului de semnare) + înregistrarea lor în Supabase Dashboard → Auth →
    providers → Google. Fără asta, nu se poate implementa.
  - **Profil dependent** (pacient vârstnic fără cont propriu) — amânat explicit din 1.5d, cere
    suport multi-profil local în Room (schimbare majoră de arhitectură).
  - Restul etapelor (1.5f audit, 1.5g teste RLS) — vezi
    `docs/user-management-plan.md` secțiunea 8, neatinse încă.
  Poziționată **înaintea** Fazei 2 pentru că schema (`patient_profile_id`) trebuia stabilă înainte
  ca Nomenclatorul/scanarea să construiască peste ea — acum e stabilă.
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
