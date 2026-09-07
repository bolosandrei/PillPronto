-- PillPronto — Row Level Security pentru schema din 0001_init_schema.sql (Faza 1.5a).
-- Vezi docs/user-management-plan.md sectiunea 3 pentru motivarea fiecarei politici.
--
-- Principiu de baza, valabil pe tot fisierul: Medic/Farmacist sunt STRICT read-only pe
-- treatments/dose_logs — nicio politica de UPDATE/DELETE pentru ei, deliberat (evita zona
-- de decizie clinica / risc de reclasificare MDR — vezi docs/user-management-plan.md sectiunea 9).

alter table public.profiles enable row level security;
alter table public.patient_profiles enable row level security;
alter table public.links enable row level security;
alter table public.treatments enable row level security;
alter table public.dose_logs enable row level security;
alter table public.audit_log enable row level security;

-- ============================================================================
-- profiles — fiecare user isi vede/edita doar propriul rand.
-- ============================================================================
create policy profiles_self on public.profiles
    for all
    using (id = auth.uid())
    with check (id = auth.uid());

-- ============================================================================
-- patient_profiles
-- ============================================================================

-- Owner (pacientul insusi, sau apartinatorul care detine un profil dependent) — acces complet.
create policy patient_profiles_owner_all on public.patient_profiles
    for all
    using (user_id = auth.uid() or owner_caregiver_id = auth.uid())
    with check (user_id = auth.uid() or owner_caregiver_id = auth.uid());

-- Oricine are un link acceptat pe acest profil — vizibilitate read-only.
create policy patient_profiles_linked_read on public.patient_profiles
    for select
    using (
        exists (
            select 1 from public.links l
            where l.patient_profile_id = patient_profiles.id
              and l.grantee_user_id = auth.uid()
              and l.status = 'accepted'
        )
    );

-- ============================================================================
-- links
-- ============================================================================

-- Owner-ul profilului de pacient gestioneaza complet legaturile (creeaza/revoca invitatii).
create policy links_owner_manage on public.links
    for all
    using (
        exists (
            select 1 from public.patient_profiles p
            where p.id = links.patient_profile_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    )
    with check (
        exists (
            select 1 from public.patient_profiles p
            where p.id = links.patient_profile_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    );

-- Grantee-ul (apartinator/medic/farmacist invitat) isi vede propriul rand de legatura.
create policy links_grantee_read on public.links
    for select
    using (grantee_user_id = auth.uid());

-- Grantee-ul poate doar accepta sau revoca propria legatura — nu poate schimba rolul/pacientul.
create policy links_grantee_respond on public.links
    for update
    using (grantee_user_id = auth.uid())
    with check (grantee_user_id = auth.uid());

-- ============================================================================
-- treatments
-- ============================================================================

create policy treatments_owner_all on public.treatments
    for all
    using (
        exists (
            select 1 from public.patient_profiles p
            where p.id = treatments.patient_profile_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    )
    with check (
        exists (
            select 1 from public.patient_profiles p
            where p.id = treatments.patient_profile_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    );

create policy treatments_linked_read on public.treatments
    for select
    using (
        exists (
            select 1 from public.links l
            where l.patient_profile_id = treatments.patient_profile_id
              and l.grantee_user_id = auth.uid()
              and l.status = 'accepted'
        )
    );

-- ============================================================================
-- dose_logs — scopat prin treatments.patient_profile_id (dose_logs nu are coloana proprie).
-- ============================================================================

create policy dose_logs_owner_all on public.dose_logs
    for all
    using (
        exists (
            select 1 from public.treatments t
            join public.patient_profiles p on p.id = t.patient_profile_id
            where t.id = dose_logs.treatment_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    )
    with check (
        exists (
            select 1 from public.treatments t
            join public.patient_profiles p on p.id = t.patient_profile_id
            where t.id = dose_logs.treatment_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    );

create policy dose_logs_linked_read on public.dose_logs
    for select
    using (
        exists (
            select 1 from public.treatments t
            join public.links l on l.patient_profile_id = t.patient_profile_id
            where t.id = dose_logs.treatment_id
              and l.grantee_user_id = auth.uid()
              and l.status = 'accepted'
        )
    );

-- ============================================================================
-- audit_log — insert de catre orice actor autentificat (aplicatia scrie la fiecare citire
-- prin `links`); citire doar de catre owner-ul profilului la care se refera intrarea.
-- ============================================================================

create policy audit_log_insert on public.audit_log
    for insert
    with check (actor_user_id = auth.uid());

create policy audit_log_owner_read on public.audit_log
    for select
    using (
        exists (
            select 1 from public.patient_profiles p
            where p.id = audit_log.patient_profile_id
              and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
        )
    );
