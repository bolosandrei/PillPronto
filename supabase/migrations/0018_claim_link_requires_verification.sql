-- claim_link — rescrisa: verifica is_verified_clinician + clinician_verification_type
-- (aprobare manuala, 0017) in loc de clinician_type (auto-declarare neverificata, 0016).
-- Conturile deja auto-declarate dar neaprobate NU mai pot revendica coduri NOI pana aprobate;
-- legaturile deja `accepted` NU sunt afectate retroactiv (functia ruleaza doar la revendicare).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0017.
--
-- Restul functiei (mesaje de eroare, update pe links, exceptie unique_violation) e identic cu
-- 0016 -- doar blocul de validare a tipului clinician se schimba.
create or replace function public.claim_link(p_invite_code text)
returns public.links
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.links;
  v_invite_role text;
  v_claimant_exists boolean;
  v_claimant_verified boolean;
  v_claimant_verified_type text;
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
         (select is_verified_clinician from public.profiles where id = auth.uid()),
         (select clinician_verification_type from public.profiles where id = auth.uid())
    into v_claimant_exists, v_claimant_verified, v_claimant_verified_type;

  if not v_claimant_exists then
    raise exception 'complete your profile before claiming an invite' using errcode = 'P0001';
  end if;

  -- `is not true`/`is distinct from` (NU `<>`) -- NULL-safe, un claimant neverificat
  -- (is_verified_clinician=false by default, sau clinician_verification_type NULL) trebuie
  -- respins explicit, nu scapat printr-o comparatie care da NULL in loc de TRUE/FALSE.
  if (v_invite_role = 'doctor' and (v_claimant_verified is not true or v_claimant_verified_type is distinct from 'doctor'))
     or (v_invite_role = 'pharmacist' and (v_claimant_verified is not true or v_claimant_verified_type is distinct from 'pharmacist'))
  then
    raise exception 'professional account verification required for this invite' using errcode = 'P0001';
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
