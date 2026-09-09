# PillPronto — Conturi, roluri și partajare (Aparținător / Medic / Farmacist)

> Document de arhitectură + plan de implementare. Scris la brainstorming-ul din 2026-09-07.
> Completează `CLAUDE.md` (nu-l duplică) — citit împreună cu acesta la sesiunile viitoare.
> Status: **1.5a + 1.5b + 1.5c + 1.5d + 1.5e implementate** (1.5a+1.5b: 2026-09-07; 1.5c-1.5e:
> 2026-09-09) — schema + RLS + migrare Room, SDK Supabase Android + autentificare email/parolă +
> onboarding rol, sync layer Room↔Supabase, legătură Pacient↔Aparținător/Medic/Farmacist read-only
> + notificare doză ratată (doar Aparținător). **Google Sign-In, profil dependent (amânat din
> 1.5d) și 1.5f-1.5g rămân neimplementate.** Vezi secțiunea 8 pentru etapele propuse și starea
> fiecăreia.

---

## 1. Decizii cheie

- **Backend: Supabase** (Postgres + Auth + Row-Level-Security + Storage + Edge Functions +
  pgvector), **regiune UE**. Motivul: domeniul e deja relațional în Room (FK cascade existent),
  modelul de acces pe roluri se mapează natural pe RLS, iar pgvector servește atât galeria de
  embeddings (Faza 4) cât și RAG-ul (Faza 6) fără infrastructură suplimentară. Detalii comparative
  Firebase vs. Supabase — vezi discuția din sesiunea de brainstorming (nu reprodusă aici).
- **Room rămâne sursa de adevăr locală** pentru dispozitivul pacientului. Remindere/alarme exacte
  (`AlarmManager`) depind azi de Room, nu de rețea — asta NU se schimbă. Supabase e un **strat de
  sincronizare opțional**, nu un înlocuitor al Room-ului.
- **4 roluri, proiectate și implementate în paralel** (decizie explicită a userului, dat fiind
  orizontul de 8-9 luni): Pacient, Aparținător, Medic, Farmacist.
- **Toate legăturile de acces trec printr-un tabel `links`** — niciun rol nu poate căuta/accesa
  liber datele unui pacient. Acces = doar prin invitație acceptată de pacient (sau de aparținătorul
  care deține profilul, în cazul unui profil dependent).

## 2. Model de date (schema conceptuală)

```
auth.users                 -- gestionat de Supabase Auth (email/parolă + Google OAuth)
profiles                   -- 1:1 cu auth.users; rol implicit: patient | caregiver | clinician
                            -- clinician_type: doctor | pharmacist | null
patient_profiles           -- id, display_name, owner_caregiver_id (nullable — profil dependent),
                            -- user_id (nullable — profil cu login propriu)
                            -- CONSTRAINT: exact unul din (owner_caregiver_id, user_id) e NOT NULL
links                      -- patient_profile_id, grantee_user_id, role, status, invite_code,
                            -- created_at, accepted_at, revoked_at
                            -- role: caregiver_viewer | caregiver_delegate | doctor | pharmacist
                            -- status: pending | accepted | revoked
treatments                 -- mirror TreatmentEntity + patient_profile_id FK, updated_at
dose_logs                  -- mirror DoseLogEntity + FK -> treatments ON DELETE CASCADE, updated_at
medication_embeddings      -- (Faza 4) patient_profile_id, embedding vector(N) [pgvector], label
rag_chunks                 -- (Faza 6) source_doc, chunk_text, embedding vector(N) [pgvector]
audit_log                  -- actor_user_id, patient_profile_id, action, entity, occurred_at
                            -- obligatoriu GDPR: "cine mi-a vazut datele, cand"
```

Note:
- `patient_profiles` e proiectat de la început să suporte **profil dependent** (aparținător
  administrează contul unui vârstnic care nu se loghează niciodată singur) — nu e un caz special
  tratat ulterior, e parte din schema de bază.
- `treatments`/`dose_logs` din Supabase sunt **oglinda** entităților Room (`TreatmentEntity`,
  `DoseLogEntity`) — aceleași câmpuri, plus `patient_profile_id` și `updated_at` pentru sync.

## 3. RLS — schiță de politici (nu SQL final, ghid pentru implementare)

- **Pacient**: `SELECT/INSERT/UPDATE/DELETE` pe `treatments`/`dose_logs` doar unde
  `patient_profile_id` corespunde profilului propriu (`auth.uid()` → `profiles` → `patient_profiles`).
- **Aparținător** (`caregiver_viewer`): `SELECT` pe `treatments`/`dose_logs` doar pentru
  `patient_profile_id`-uri cu rând `links` `status = 'accepted'` și `grantee_user_id = auth.uid()`.
- **Aparținător delegat** (`caregiver_delegate`, v2 — vezi secțiunea 9): în plus, `INSERT` pe
  `dose_logs` (logare în numele pacientului) — **NU** `UPDATE`/`DELETE` pe `treatments` (nu editează
  planul de tratament din v1).
- **Medic/Farmacist**: `SELECT` read-only, aceeași condiție prin `links`. **Fără** drept de
  scriere pe `treatments`/`dose_logs` — decizie deliberată, evită zona de decizie clinică (risc de
  clasificare MDR, vezi secțiunea 9).
- Fiecare `SELECT` prin `links` scrie un rând în `audit_log` (trigger sau la nivel de aplicație).

## 4. Sincronizare Room ↔ Supabase

- Scriere locală **imediată** în Room (UI responsiv, alarmele merg 100% offline, neschimbat).
- `SyncWorker` nou, alături de `AdherenceMaintenanceWorker`/`MaintenanceScheduler` existente
  (`data/work/`) — outbox pattern: push periodic al modificărilor locale + pull al modificărilor
  de la profilurile legate (relevant mai ales pentru aparținător/medic, care nu au alarme locale
  de sincronizat, doar vizibilitate).
- Rezolvare conflicte: **last-write-wins** pe `updated_at` — suficient pt. MVP; `dose_logs` e
  practic append-only (o doză se creează, apoi tranziționează o singură dată de status), risc de
  conflict real e mic.
- Fără conexiune: aplicația funcționează identic cu azi (remindere, logare doze) — sync-ul
  recuperează la următoarea fereastră de conectivitate.

## 5. Autentificare

- Supabase Auth: email/parolă (✅ 1.5b) + Google Sign-In (deferred — vezi 1.5b în secțiunea 8).
- **Profil dependent** (pacient vârstnic fără cont propriu): NU necesită `auth.users` separat —
  doar un rând `patient_profiles` cu `owner_caregiver_id` populat. Telefonul pacientului rămâne
  logat pe contul aparținătorului (sau pe un mod "device pacient" fără ecran de login vizibil).

## 6. Ce vede/poate face fiecare rol

| Rol | Vizibilitate | Scriere | Notificări |
|---|---|---|---|
| Pacient | propriile date, complet | complet | remindere doze (existent) |
| Aparținător (viewer) | pacienți legați, aderență + tratamente | — | doză ratată (nou) |
| Aparținător (delegate, v2) | + poate loga doză pt. pacient | `dose_logs` INSERT | doză ratată |
| Medic | pacienți legați, aderență + tratament activ, read-only | — | — (export/print, nu push) |
| Farmacist | pacienți legați, tratament activ + alerte interacțiuni (leagă de Faza 6) | — | — |

## 7. Compatibilitate cu Fazele 2-7 (roadmap existent)

Confirmat compatibil, fără blocaje — detaliat per fază în discuția de brainstorming; rezumat:
Fazele 3 și 5 sunt 100% on-device (inferență/tracking), zero interacțiune cu backend-ul. Faza 2
folosește Storage doar pt. distribuția Nomenclatorului. Fazele 4 și 6 beneficiază direct de
pgvector (galerie embeddings + RAG, aceeași infrastructură). Faza 7 beneficiază de cascade delete
SQL nativ și de analiza directă (SQL/pandas) a datelor din studiul pilot pentru Cap. 5 din teză.

## 8. Plan de implementare — „Faza 1.5" (înaintea Fazei 2)

Motivul poziționării înaintea Fazei 2: schema trebuie stabilă înainte ca Nomenclatorul/scanarea să
construiască peste ea (toate vor referi `patient_profile_id`).

- **1.5a — Fundație ✅ IMPLEMENTAT (2026-09-07):**
  - `patientProfileId: String` adăugat în `TreatmentEntity`/`DoseLogEntity`, generat local
    (UUID, `LocalPatientProfileProvider` — devine `patient_profiles.id` la 1.5b, fără reconciliere).
    `PillProntoDatabase` la `version = 3`.
  - Schema SQL + RLS scrise în `supabase/migrations/0001_init_schema.sql` +
    `0002_rls_policies.sql` — **executate** de utilizator pe proiectul Supabase real (regiune UE).
- **1.5b — Auth Android email/parolă + onboarding ✅ IMPLEMENTAT (2026-09-07):**
  - SDK Supabase Kotlin conectat (`SupabaseModule`, BOM 3.5.0, Auth + Postgrest), credențiale din
    `local.properties` → `BuildConfig` (niciodată în cod). `compileSdk` ridicat la 36
    (`androidx.browser`, adus tranzitiv de `auth-kt`, o cere).
  - Strat domeniu: `AuthRepository`/`ProfileRepository` + use-cases (`SignUpUseCase`,
    `SignInUseCase`, `SignOutUseCase`, `ObserveAuthSessionUseCase`, `GetProfileUseCase`,
    `CompleteOnboardingUseCase`); `AuthSessionState` traduce `SessionStatus` din SDK — vendor-ul
    nu se scurge în domeniu.
  - Tab nou „Cont" (al 4-lea, bottom bar) — `AccountScreen` (toggle autentificare/înregistrare,
    validare tipizată `AccountError`) + `OnboardingScreen` (alegere rol, scrie `profiles` +
    `patient_profiles` doar pt. Pacient — vezi decizia despre cei doi UUID diferiți, secțiunea 2).
  - **Cont opțional, nu obligatoriu** — aplicația rămâne 100% funcțională fără login (decizie
    explicită, consecventă cu minimizarea GDPR deja stabilită).
  - **Exclus deliberat, urmează separat:** Google Sign-In (necesită 2 OAuth Client ID-uri distincte
    în Google Cloud Console — Web + Android cu SHA-1 — plus înregistrarea lor în Supabase
    Dashboard; blocaj extern separat de cel de la 1.5a).
  - Teste: `AccountViewModelTest` (9), `OnboardingViewModelTest` (5) — `FakeAuthRepository`,
    `FakeProfileRepository`, `MainDispatcherRule` noi în `util/` (primul ViewModel testat cu
    `viewModelScope` din proiect).
  - **Bug-uri găsite și rezolvate în testarea pe device reală** (2026-09-07, cont real creat):
    1. `Log.e(...)` arunca "not mocked" în testele JVM (fără Robolectric) și mânca silențios
       rezultatul unui test — fix: `testOptions.unitTests.isReturnDefaultValues = true`.
    2. Limita implicită Supabase de 2-4 emailuri/oră (SMTP shared, gratuit) apărea ca eroare
       generică `AUTH_FAILED` — fix: `AccountError.RATE_LIMITED` distinct, detectat din mesajul
       excepției (`AuthRestException` cu `over_email_send_rate_limit`).
    3. **Bug real, nu artefact de testare**: `AccountViewModel` reface profilul doar la *schimbarea*
       sesiunii, nu și după un onboarding reușit cât timp sesiunea era deja `Authenticated` —
       userul era retrimis la nesfârșit pe un ecran de onboarding gol după ce contul chiar fusese
       creat cu succes, iar o reîncercare lovea coliziune de cheie primară. Fix: `AccountViewModel.refresh()`
       public, apelat din `AccountScreen` via `LifecycleEventEffect(ON_RESUME)` + `completeOnboarding`
       schimbat din `insert` în `upsert` (idempotent, tolerează reîncercări).
- **1.5c — Sync layer ✅ IMPLEMENTAT (2026-09-09):**
  - Sincronizare bidirecțională a **propriilor** date ale Pacientului (`treatments`+`dose_logs`
    cu status final) — Room rămâne sursa de adevăr locală, Supabase e strat opțional. Pull de la
    profiluri legate (Aparținător/Medic) rămâne pentru 1.5d/e, nu e parte din 1.5c.
  - **Decizie de scop:** dozele PENDING nu se sincronizează (stare de programare locală, nu
    istoric de aderență — doar TAKEN/MISSED/SKIPPED merg la Supabase). Elimină nevoia de
    tombstone-uri pentru `deleteFuturePending` (șterge doar PENDING viitoare, niciodată push-uite).
  - Outbox pattern: `TreatmentEntity`/`DoseLogEntity` +`remoteId`/`updatedAt`/`dirty`
    (`PillProntoDatabase` v4), tabel nou `pending_remote_deletes` pentru ștergeri de propagat.
    Conflict resolution: last-write-wins pe `updatedAt`, un rând local `dirty` nu e niciodată
    suprascris de un pull.
  - `data/sync/SyncManager.kt` (coordonator, precedent `ReminderCoordinator`) — ordine strictă:
    delete-uri → push treatments → push dose_logs → pull treatments → pull dose_logs →
    regenerare doze PENDING pentru tratamentele nou-scrise din pull → resincronizare alarme.
    `data/sync/SyncRemoteDataSource.kt`/`SupabaseSyncDataSource.kt` abstractizează Postgrest.
  - `data/work/SyncWorker.kt` — periodic 30 min + o dată la pornirea aplicației (`PillProntoApp`),
    no-op sigur dacă userul nu e autentificat ca Pacient.
  - Seam-uri de testabilitate: `ReminderSync` (peste `ReminderCoordinator`) și
    `PatientProfileIdProvider` (peste `LocalPatientProfileProvider`) — ambele clase concrete
    originale ating Android framework (`AlarmManager`/`SharedPreferences` via `Context`) în
    constructor, netestabile direct în JVM.
  - Bug găsit în plan review, fixat înainte de implementare: `markOverdueMissed` (DAO) nu seta
    `dirty`/`updatedAt` la tranziția PENDING→MISSED — fără fix, dozele ratate nu s-ar fi
    sincronizat niciodată.
  - Teste: `SyncManagerTest` (11 cazuri), fake-uri noi `FakeTreatmentDao`/`FakeDoseDao`/
    `FakePendingRemoteDeleteDao`/`FakeSyncRemoteDataSource`/`FakeReminderSync`/
    `FakePatientProfileIdProvider` în `util/`.
  - **Neverificat încă pe device fizic** (Supabase Dashboard) — rămâne de făcut manual.
- **1.5d — Flux Aparținător (viewer) ✅ IMPLEMENTAT (2026-09-09):**
  - Scop v1, decis explicit cu utilizatorul: **doar** Aparținător ↔ Pacient cu cont propriu,
    read-only. „Profil dependent" (pacient vârstnic fără cont propriu) **amânat** — ar cere suport
    multi-profil local în Room, schimbare majoră separată.
  - **Migrare `supabase/migrations/0003_links_open_invite.sql`** — `grantee_user_id` pe `links`
    devine nullable; funcție `claim_link(p_invite_code) SECURITY DEFINER` pentru revendicarea
    invitației (nu o politică RLS de UPDATE — verificat împotriva documentației PostgreSQL că
    UPDATE cu WHERE cere vizibilitate SELECT separată pe rândul țintă, iar o politică de SELECT
    „toate rândurile pending nerevendicate" ar permite enumerarea tuturor codurilor active,
    oricui autentificat); trigger `links_guard_update` — hardening suplimentar, închide o gaură
    preexistentă din `links_grantee_respond` (1.5a) care nu împiedica un grantee să-și schimbe
    propriul rând `links` către alt `patient_profile_id`/`role`. Găsit în plan review, nu în
    cererea inițială — vezi CLAUDE.md secțiunea 7 pentru detalii complete.
  - Cod de invitație: text simplu, 8 caractere (fără 0/O/1/I), `SecureRandom`, distribuit prin
    Android share sheet — fără QR vizual în v1 (fără dependență nouă).
  - `LinkRepository`/`LinkedPatientDataRepository` (citire remote directă, niciodată din Room
    local) + `AdherenceCalculator` extras din `ComputeAdherenceUseCase` (formulă PDC/MPR pură,
    reutilizată și pentru loguri remote).
  - `CaregiverAlertWorker` (periodic 30 min, no-op dacă rolul != CAREGIVER) + `MissedDoseChecker`
    (deduplicare pe mulțime de `remoteId`, nu pe timestamp — status MISSED e terminal).
  - UI fără tab nou în bottom bar — buton condiționat de rol în `AccountScreen`:
    `ui/access/ManageAccessScreen` (Pacient), `ui/patients/MyPatientsScreen` +
    `PatientDetailScreen` (Apartinător, read-only).
  - Teste: 21 cazuri noi (`AdherenceCalculatorTest`, `MissedDoseCheckerTest`,
    `GetLinkedPatientAdherenceUseCaseTest`, `ManageAccessViewModelTest`, `MyPatientsViewModelTest`,
    `PatientDetailViewModelTest`), toate trec.
  - **Migrare `supabase/migrations/0004_fix_links_rls_recursion.sql`** — bug real găsit la primul
    test manual pe device (2026-09-09): „Generează cod nou" întorcea eroare Postgres `infinite
    recursion detected in policy for relation "links"` (cod `42P17`). Cauză, prezentă din 1.5a
    (`0002_rls_policies.sql`), nedescoperită pentru că nimic nu interogase direct
    `links`/`treatments`/`dose_logs` până la 1.5d: `links_owner_manage` subqueria
    `patient_profiles`, iar `patient_profiles_linked_read` subqueria invers `links` — RLS se
    reevaluează tranzitiv la fiecare acces la tabel, deci evaluarea uneia declanșa evaluarea
    celeilalte, la nesfârșit. Același tipar exista între `treatments`/`links` și
    `dose_logs`/`treatments`/`patient_profiles`/`links` — ar fi blocat probabil și sync-ul din
    1.5c. Fix: funcții `SECURITY DEFINER` care ocolesc RLS intern, înlocuind subquery-urile
    corelate din politici.
  - **Testat parțial pe device fizic** — primul test manual (generare cod) a găsit bug-ul de mai
    sus. Migrările 0003+0004 trebuie rulate de utilizator (în această ordine) înainte ca fluxul
    complet (invitație → claim → vizibilitate → notificare) să funcționeze end-to-end.
  - **Al doilea bug găsit la testare** (cont Apartinător nou, onboarding): condiție de cursă
    rămasă în fix-ul din 1.5b (`AccountViewModel.refreshProfile` nu marca sincron „verificare în
    curs" înainte de fetch-ul async) — userul era retrimis direct pe onboarding după ce-l termina
    cu succes. Fix + detalii complete: `CLAUDE.md` secțiunea 7 (blocul 1.5d), test nou
    `AccountViewModelTest`.
  - **Confirmat funcțional end-to-end pe device** după fix-uri; utilizatorul a cerut apoi
    reducerea fricțiunii la legare (cod de introdus manual = "tedios") + vizibilitate identitate.
  - **Rafinare UX**: deep link `pillpronto://invite?code=...` (schemă proprie, `ui/access/InviteLink.kt`)
    + cod QR (`com.google.zxing:core`, doar generare) ca mecanism principal „fără tastare" — link-ul
    text NU e garantat clicabil în WhatsApp/SMS (auto-linkify doar pe `http(s)://`), QR-ul ocolește
    problema complet. Nume Aparținător vizibil Pacientului — migrare nouă
    `0005_profiles_visible_to_linked_grantee.sql`. Detalii complete: `CLAUDE.md` secțiunea 7.
- **1.5e — Flux Medic/Farmacist ✅ IMPLEMENTAT (2026-09-09):**
  - Infrastructura de citire (`MyPatientsScreen`/`PatientDetailScreen`, RLS) era deja agnostică la
    rol din 1.5d — reutilizată integral, zero schimbări. `CaregiverAlertWorker` rămâne strict
    pentru Aparținător (Medic/Farmacist nu primesc notificări, conform tabelului din secțiunea 6).
  - Pacientul alege explicit rolul (Aparținător/Medic/Farmacist) la generarea codului —
    `createInvite`/`claim_link` (migrare nouă `0007_professional_invite_role_check.sql`) validează
    că rolul contului care revendică se potrivește cu cel declarat.
  - Ecran separat `ManageProfessionalAccessScreen` (decizie explicită a utilizatorului, nu unificat
    cu ecranul Aparținătorilor) — selector Medic/Farmacist, altfel aceeași structură.
    `AccessLinkComponents.kt` extrage piesele reutilizabile (rând legătură, QR, distribuire) între
    cele două ecrane.
  - Flag „neverificat" (auto-declarat, fără validare CUIM — vezi limitarea din secțiunea 9) vizibil
    atât pe lista Pacientului cât și pe propriul cont al Medicului/Farmacistului.
  - Teste: `ManageProfessionalAccessViewModelTest` (7 cazuri noi) + extindere
    `ManageAccessViewModelTest`, toate trec.
  - **Neverificat încă pe device** — migrarea 0007 trebuie rulată de utilizator (după 0001-0006).
- **1.5f — Audit & consimțământ:** `audit_log` populat automat, ecran „Cine îmi vede datele"
  (revocare acces) — obligatoriu GDPR, nu opțional.
- **1.5g — Teste:** RLS policy tests (pgTAP sau echivalent), teste de integrare sync, teste unitare
  pentru logica de conflict/outbox.

## 9. Ce NU rezolvăm acum (limitări cunoscute, de discutat explicit în teză)

- **Verificarea reală a identității medic/farmacist** (nr. de ordin, CUIM) — necesită un registru
  oficial la care nu avem acces garantat. Abordare realistă pentru pilot: auto-declarare + flag
  „neverificat" vizibil în UI, eventual document justificativ verificat manual în studiul pilot.
  Discutat ca limitare cunoscută în Cap. 6 (aspecte legale).
- **Aparținător delegat (scriere doze în numele pacientului)** — proiectat în schemă (RLS pregătit),
  dar amânat ca v2; v1 e read-only + notificări, suficient pentru valoarea principală.
- **Drept de editare a tratamentului pentru medic** — evitat deliberat, ar apropia aplicația de
  zona de decizie clinică (risc de reclasificare MDR). Rămâne read-only + export.
