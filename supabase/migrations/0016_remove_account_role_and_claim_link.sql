-- PillPronto — elimina conceptul de "rol unic de cont" (Faza 1.5h, capabilitati coexistente).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0015.
--
-- Context: pana acum, `profiles.role` (patient/caregiver/clinician) era un scalar unic, impus la
-- onboarding si verificat in `claim_link` (0007) — un cont nu putea fi simultan Pacient (pentru
-- sine) SI Apartinator/Medic/Farmacist (pentru altii). Decizie (vezi planul complet al Fazei 1.5h):
--   - Fiecare cont autentificat e implicit Pacient — capacitatea nu mai e un rand/flag separat,
--     e derivata din simpla existenta a unui rand `patient_profiles` pentru acel user_id.
--   - Capacitatea de Apartinator/Medic/Farmacist se deriva din `links` (>=1 rand accepted ca
--     grantee) — deja asa functioneaza `getMyPatients`/RLS, neschimbat aici.
--   - `clinician_type` RAMANE (auto-declarare optionala, independenta de orice "rol"), folosit
--     STRICT ca poarta pt. revendicarea unei invitatii de tip doctor/pharmacist.
-- RLS pe patient_profiles/links/treatments/dose_logs/audit_log NU se schimba — nu verifica deloc
-- profiles.role (confirmat prin audit de cod), doar `claim_link` avea aceasta cuplare.

alter table public.profiles drop constraint profiles_clinician_type_matches_role;
alter table public.profiles drop column role;

-- ============================================================================
-- claim_link — rescrisa: nu mai cere potrivire de "rol de cont", doar (pt. doctor/pharmacist)
-- ca declaratia proprie (clinician_type) sa se potriveasca tipului declarat al invitatiei.
-- Acceptarea unei invitatii de Apartinator nu mai cere nicio auto-declarare in prealabil —
-- acceptarea insasi e suficienta (simetric cu cum functiona deja INAINTE de 0007).
-- ============================================================================
create or replace function public.claim_link(p_invite_code text)
returns public.links
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.links;
  v_invite_role text;
  v_claimant_clinician_type text;
  v_claimant_exists boolean;
begin
  if p_invite_code is null or length(p_invite_code) = 0 then
    raise exception 'invite code required' using errcode = 'P0001';
  end if;

  select role into v_invite_role
    from public.links
   where invite_code = p_invite_code
     and status = 'pending'
     and grantee_user_id is null;

  if v_invite_role is null then
    raise exception 'invite code invalid or already used' using errcode = 'P0001';
  end if;

  select exists (select 1 from public.profiles where id = auth.uid()),
         (select clinician_type from public.profiles where id = auth.uid())
    into v_claimant_exists, v_claimant_clinician_type;

  if not v_claimant_exists then
    raise exception 'complete your profile before claiming an invite' using errcode = 'P0001';
  end if;

  -- `is distinct from`, NU `<>` — daca claimant-ul nu si-a declarat niciodata un clinician_type
  -- (NULL), `<>` ar da NULL (nu TRUE), iar `if NULL then` e tratat ca FALSE in PL/pgSQL — ar lasa
  -- un cont nedeclarat sa revendice o invitatie de doctor/pharmacist, exact gaura pe care 0007 o
  -- inchisese initial. `is distinct from` trateaza NULL corect ca "diferit".
  if (v_invite_role = 'doctor' and v_claimant_clinician_type is distinct from 'doctor')
     or (v_invite_role = 'pharmacist' and v_claimant_clinician_type is distinct from 'pharmacist')
  then
    raise exception 'this invite is for a different account type' using errcode = 'P0001';
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
exception
  when unique_violation then
    raise exception 'you already have an active link to this patient' using errcode = 'P0001';
end;
$$;
