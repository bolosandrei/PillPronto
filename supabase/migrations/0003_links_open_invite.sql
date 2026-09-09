-- PillPronto — invitatie deschisa Pacient->Apartinator (Faza 1.5d).
-- Vezi docs/user-management-plan.md sectiunea 8 (1.5d) pentru contextul complet al deciziilor.
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001_init_schema.sql si 0002_rls_policies.sql.
--
-- Motivatie: fluxul ales e "Pacientul genereaza un cod fara sa stie cine il va folosi,
-- Apartinatorul il revendica mai tarziu". O politica RLS simpla de UPDATE nu poate implementa
-- corect asta: un UPDATE cu clauza WHERE cere ca randul tinta sa fie deja vizibil printr-o
-- politica de SELECT separata (nu doar de UPDATE) — iar o politica de SELECT "oricine vede
-- randurile pending nerevendicate" ar scurge toate codurile de invitatie active oricui e
-- autentificat (enumerare), anuland modelul de securitate "acces doar prin posesia codului".
--
-- Solutie: functie SECURITY DEFINER care ocoleste RLS intern, accepta doar codul ca parametru
-- si hardcodeaza exact ce coloane se modifica — clientul nu poate influenta role/patient_profile_id
-- prin payload, iar enumerarea e imposibila (singura cale de acces e egalitate exacta pe cod, in
-- interiorul functiei).

alter table public.links alter column grantee_user_id drop not null;

-- ============================================================================
-- claim_link — revendicarea unei invitatii pending, nerevendicate, prin codul exact.
-- ============================================================================
create or replace function public.claim_link(p_invite_code text)
returns public.links
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.links;
begin
  if p_invite_code is null or length(p_invite_code) = 0 then
    raise exception 'invite code required' using errcode = 'P0001';
  end if;

  update public.links
     set grantee_user_id = auth.uid(),
         status = 'accepted',
         accepted_at = now()
   where invite_code = p_invite_code
     and status = 'pending'
     and grantee_user_id is null
  returning * into v_link;

  if v_link.id is null then
    raise exception 'invite code invalid or already used' using errcode = 'P0001';
  end if;

  return v_link;
end;
$$;

revoke all on function public.claim_link(text) from public;
grant execute on function public.claim_link(text) to authenticated;

-- ============================================================================
-- links_guard_update — hardening: inchide o gaura preexistenta din 0002_rls_policies.sql.
-- `links_grantee_respond` (FOR UPDATE USING/WITH CHECK grantee_user_id=auth.uid()) nu impiedica
-- azi un grantee sa-si schimbe propriul rand `links` catre alt patient_profile_id sau alt role in
-- acelasi UPDATE — WITH CHECK-ul actual doar reasserta grantee_user_id, nu si celelalte coloane.
-- Trigger-ul face invite_code/patient_profile_id imuabile pe UPDATE si limiteaza schimbarea
-- role la proprietarul profilului de pacient (acelasi criteriu ca links_owner_manage).
-- ============================================================================
create or replace function public.links_guard_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_is_owner boolean;
begin
  if new.invite_code is distinct from old.invite_code then
    raise exception 'links.invite_code is immutable';
  end if;
  if new.patient_profile_id is distinct from old.patient_profile_id then
    raise exception 'links.patient_profile_id is immutable';
  end if;
  if new.role is distinct from old.role then
    select exists (
      select 1 from public.patient_profiles p
      where p.id = old.patient_profile_id
        and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
    ) into v_is_owner;
    if not v_is_owner then
      raise exception 'links.role can only be changed by the patient profile owner';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists links_guard_update_trigger on public.links;
create trigger links_guard_update_trigger
    before update on public.links
    for each row execute function public.links_guard_update();
