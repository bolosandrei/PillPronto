-- PillPronto — schema initiala pentru conturi & roluri (Faza 1.5a).
-- Vezi docs/user-management-plan.md pentru contextul complet al deciziilor de mai jos.
--
-- De rulat in Supabase Dashboard -> SQL Editor, pe un proiect nou creat in regiune UE,
-- INAINTEA lui 0002_rls_policies.sql.

-- ============================================================================
-- profiles — extensie a auth.users cu rolul aplicatiei (nu inlocuieste auth.users)
-- ============================================================================
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    role text not null check (role in ('patient', 'caregiver', 'clinician')),
    clinician_type text check (clinician_type in ('doctor', 'pharmacist')),
    display_name text,
    created_at timestamptz not null default now(),
    constraint profiles_clinician_type_matches_role check (
        (role = 'clinician' and clinician_type is not null) or
        (role != 'clinician' and clinician_type is null)
    )
);

-- ============================================================================
-- patient_profiles — un profil de pacient, fie legat de un cont propriu (user_id),
-- fie "dependent" si detinut integral de un Apartinator (owner_caregiver_id) — pentru
-- pacientii care nu-si gestioneaza singuri contul (ex. varstnici). Exact unul din cele
-- doua e populat, niciodata amandoua sau niciunul.
-- ============================================================================
create table public.patient_profiles (
    id uuid primary key default gen_random_uuid(),
    display_name text not null,
    user_id uuid references auth.users(id) on delete cascade,
    owner_caregiver_id uuid references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint patient_profiles_exactly_one_owner check (
        (user_id is not null and owner_caregiver_id is null) or
        (user_id is null and owner_caregiver_id is not null)
    )
);

-- ============================================================================
-- links — acces many-to-many intre un patient_profile si un user (Apartinator/Medic/
-- Farmacist). Singura poarta de acces pentru rolurile non-pacient — niciun rol nu poate
-- cauta/accesa liber datele unui pacient in afara unui rand `links` acceptat.
-- ============================================================================
create table public.links (
    id uuid primary key default gen_random_uuid(),
    patient_profile_id uuid not null references public.patient_profiles(id) on delete cascade,
    grantee_user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('caregiver_viewer', 'caregiver_delegate', 'doctor', 'pharmacist')),
    status text not null default 'pending' check (status in ('pending', 'accepted', 'revoked')),
    invite_code text unique,
    created_at timestamptz not null default now(),
    accepted_at timestamptz,
    revoked_at timestamptz,
    unique (patient_profile_id, grantee_user_id)
);

-- ============================================================================
-- treatments / dose_logs — oglinda TreatmentEntity/DoseLogEntity din Room (app/src/main/
-- java/com/pillpronto/data/local/entity/). Cheile locale sunt Long (SQLite autoincrement);
-- aici sunt uuid, pt. ca id-urile trebuie generabile client-side inainte de sincronizare
-- (outbox pattern, vezi docs/user-management-plan.md sectiunea 4).
-- ============================================================================
create table public.treatments (
    id uuid primary key default gen_random_uuid(),
    patient_profile_id uuid not null references public.patient_profiles(id) on delete cascade,
    medication_name text not null,
    dosage text not null,
    times_csv text not null default '',
    start_date date not null,
    end_date date,
    active boolean not null default true,
    as_needed boolean not null default false,
    updated_at timestamptz not null default now()
);

create table public.dose_logs (
    id uuid primary key default gen_random_uuid(),
    treatment_id uuid not null references public.treatments(id) on delete cascade,
    scheduled_at timestamptz not null,
    status text not null check (status in ('PENDING', 'TAKEN', 'MISSED', 'SKIPPED')),
    taken_at timestamptz,
    is_as_needed boolean not null default false,
    updated_at timestamptz not null default now()
);

create index dose_logs_treatment_id_idx on public.dose_logs (treatment_id);
create index dose_logs_scheduled_at_idx on public.dose_logs (scheduled_at);
create index treatments_patient_profile_id_idx on public.treatments (patient_profile_id);
create index links_grantee_user_id_idx on public.links (grantee_user_id);

-- ============================================================================
-- audit_log — cine a vazut datele cui, cand. Obligatoriu GDPR pentru acces partajat pe
-- date de sanatate (Art. 9) — nu optional. Populat de aplicatie la fiecare citire prin
-- `links` (nu doar prin trigger SQL, pentru ca "citire" e o actiune de aplicatie, nu de DB).
-- ============================================================================
create table public.audit_log (
    id bigint generated always as identity primary key,
    actor_user_id uuid not null references auth.users(id) on delete cascade,
    patient_profile_id uuid not null references public.patient_profiles(id) on delete cascade,
    action text not null,
    entity text,
    occurred_at timestamptz not null default now()
);
