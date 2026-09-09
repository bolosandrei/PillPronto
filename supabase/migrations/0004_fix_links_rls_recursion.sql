-- PillPronto — fix pentru recursivitate infinita in politicile RLS (bug real, gasit la
-- testarea pe device a Fazei 1.5d — "infinite recursion detected in policy for relation links",
-- Postgres error code 42P17).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001/0002/0003.
--
-- ============================================================================
-- Cauza (prezenta inca din 0002_rls_policies.sql, Faza 1.5a — nedescoperita pana acum pentru ca
-- niciun cod al aplicatiei n-a interogat direct `links`/`treatments`/`dose_logs` intr-un context
-- care sa declanseze bucla, pana la 1.5d):
--
-- `links_owner_manage` (pe `links`) face un subquery corelat pe `patient_profiles`.
-- `patient_profiles_linked_read` (pe `patient_profiles`) face un subquery corelat pe `links`.
-- RLS se reevalueaza tranzitiv la fiecare acces la un tabel, inclusiv dintr-un subquery aflat
-- in interiorul politicii altui tabel — deci evaluarea `links_owner_manage` declanseaza RLS pe
-- `patient_profiles`, care (prin `patient_profiles_linked_read`) declanseaza din nou RLS pe
-- `links`, la nesfarsit. Postgres detecteaza bucla si arunca eroarea in loc sa agate conexiunea.
-- Acelasi tipar exista intre `treatments`/`links` (`treatments_owner_all` + `treatments_linked_read`)
-- si intre `dose_logs`/`treatments`/`patient_profiles`/`links` — nu doar pe `links`.
--
-- Solutie standard Postgres/Supabase pentru acest caz: functii `SECURITY DEFINER` care ocolesc
-- RLS intern (ruleaza cu drepturile proprietarului tabelelor) pentru verificarile cross-table,
-- in loc de subquery-uri corelate directe in politici — rupe bucla complet.
-- ============================================================================

create or replace function public.is_patient_profile_owner(p_patient_profile_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.patient_profiles p
        where p.id = p_patient_profile_id
          and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
    );
$$;

create or replace function public.has_accepted_link(p_patient_profile_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.links l
        where l.patient_profile_id = p_patient_profile_id
          and l.grantee_user_id = auth.uid()
          and l.status = 'accepted'
    );
$$;

create or replace function public.is_treatment_owner(p_treatment_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.treatments t
        join public.patient_profiles p on p.id = t.patient_profile_id
        where t.id = p_treatment_id
          and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
    );
$$;

create or replace function public.has_accepted_link_for_treatment(p_treatment_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.treatments t
        join public.links l on l.patient_profile_id = t.patient_profile_id
        where t.id = p_treatment_id
          and l.grantee_user_id = auth.uid()
          and l.status = 'accepted'
    );
$$;

revoke all on function public.is_patient_profile_owner(uuid) from public;
revoke all on function public.has_accepted_link(uuid) from public;
revoke all on function public.is_treatment_owner(uuid) from public;
revoke all on function public.has_accepted_link_for_treatment(uuid) from public;
grant execute on function public.is_patient_profile_owner(uuid) to authenticated;
grant execute on function public.has_accepted_link(uuid) to authenticated;
grant execute on function public.is_treatment_owner(uuid) to authenticated;
grant execute on function public.has_accepted_link_for_treatment(uuid) to authenticated;

-- ============================================================================
-- Rescrie politicile care faceau subquery direct pe un tabel a carui RLS refera inapoi.
-- ============================================================================

drop policy if exists links_owner_manage on public.links;
create policy links_owner_manage on public.links
    for all
    using (public.is_patient_profile_owner(patient_profile_id))
    with check (public.is_patient_profile_owner(patient_profile_id));

drop policy if exists patient_profiles_linked_read on public.patient_profiles;
create policy patient_profiles_linked_read on public.patient_profiles
    for select
    using (public.has_accepted_link(id));

drop policy if exists treatments_owner_all on public.treatments;
create policy treatments_owner_all on public.treatments
    for all
    using (public.is_patient_profile_owner(patient_profile_id))
    with check (public.is_patient_profile_owner(patient_profile_id));

drop policy if exists treatments_linked_read on public.treatments;
create policy treatments_linked_read on public.treatments
    for select
    using (public.has_accepted_link(patient_profile_id));

drop policy if exists dose_logs_owner_all on public.dose_logs;
create policy dose_logs_owner_all on public.dose_logs
    for all
    using (public.is_treatment_owner(treatment_id))
    with check (public.is_treatment_owner(treatment_id));

drop policy if exists dose_logs_linked_read on public.dose_logs;
create policy dose_logs_linked_read on public.dose_logs
    for select
    using (public.has_accepted_link_for_treatment(treatment_id));

-- audit_log_owner_read subqueria direct patient_profiles — nu era in bucla propriu-zisa (nu trece
-- prin links), dar il aliniem la acelasi helper pentru consecventa si ca sa nu mai depinda deloc
-- de "RLS pe patient_profiles ramane sigur de evaluat" ca presupunere implicita.
drop policy if exists audit_log_owner_read on public.audit_log;
create policy audit_log_owner_read on public.audit_log
    for select
    using (public.is_patient_profile_owner(patient_profile_id));

-- ============================================================================
-- links_guard_update (0003_links_open_invite.sql) facea acelasi subquery corelat pe
-- patient_profiles, in interiorul unui trigger `security invoker` — risca aceeasi recursivitate
-- la orice UPDATE pe `links` (ex. revokeLink). Il aliniem la helper-ul SECURITY DEFINER.
-- ============================================================================
create or replace function public.links_guard_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if new.invite_code is distinct from old.invite_code then
    raise exception 'links.invite_code is immutable';
  end if;
  if new.patient_profile_id is distinct from old.patient_profile_id then
    raise exception 'links.patient_profile_id is immutable';
  end if;
  if new.role is distinct from old.role then
    if not public.is_patient_profile_owner(old.patient_profile_id) then
      raise exception 'links.role can only be changed by the patient profile owner';
    end if;
  end if;
  return new;
end;
$$;
