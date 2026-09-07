# PillPronto — Conturi, roluri și partajare (Aparținător / Medic / Farmacist)

> Document de arhitectură + plan de implementare. Scris la brainstorming-ul din 2026-09-07.
> Completează `CLAUDE.md` (nu-l duplică) — citit împreună cu acesta la sesiunile viitoare.
> Status: **1.5a implementată** (2026-09-07) — schema + RLS + migrare Room. **1.5b-1.5g rămân
> neimplementate.** Vezi secțiunea 8 pentru etapele propuse și starea fiecăreia.

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

- Supabase Auth: email/parolă + Google Sign-In.
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
    `0002_rls_policies.sql` — **neexecutate încă**, necesită proiect Supabase real (regiune UE).
  - **Rămas de făcut de utilizator, în afara codului:** cont + proiect nou pe supabase.com
    (regiune UE), rulare celor 2 fișiere SQL via Dashboard → SQL Editor, notare `Project URL` +
    `anon public key` pentru 1.5b.
- **1.5b — Auth Android:** ecrane login/signup (email+parolă, Google Sign-In), onboarding
  ("Sunt pacient" vs. "Sunt aparținător/profesionist") — integrare Supabase Kotlin SDK.
- **1.5c — Sync layer:** `SyncWorker`, outbox local, pull la pornire + periodic.
- **1.5d — Flux Aparținător:** creare profil dependent, invitație (cod/QR), ecran „Pacienții mei"
  cu situația curentă per pacient, notificare la doză ratată.
- **1.5e — Flux Medic/Farmacist:** onboarding profesionist (auto-declarat + flag „neverificat"
  vizibil — vezi limitarea din secțiunea 9), dashboard read-only pe pacienții legați.
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
