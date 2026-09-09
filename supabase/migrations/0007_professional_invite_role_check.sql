-- PillPronto — valideaza ca rolul contului care revendica o invitatie se potriveste cu rolul
-- declarat al invitatiei (Faza 1.5e — Pacientul alege acum explicit Apartinator/Medic/Farmacist
-- la generarea codului, vezi ManageProfessionalAccessScreen).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001-0006.
--
-- Fara aceasta validare, orice cont ar putea revendica orice cod, indiferent de rolul declarat
-- de Pacient la generare (`links.role`) — coloana ar deveni doar decorativa, inconsecventa cu
-- rolul real al contului grantee.

create or replace function public.claim_link(p_invite_code text)
returns public.links
language plpgsql
security definer
set search_path = public
as $$
declare
  v_link public.links;
  v_invite_role text;
  v_claimant_role text;
  v_claimant_clinician_type text;
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

  select role, clinician_type into v_claimant_role, v_claimant_clinician_type
    from public.profiles
   where id = auth.uid();

  if v_claimant_role is null then
    raise exception 'complete onboarding before claiming an invite' using errcode = 'P0001';
  end if;

  if (v_invite_role = 'caregiver_viewer' and v_claimant_role <> 'caregiver')
     or (v_invite_role = 'doctor' and (v_claimant_role <> 'clinician' or v_claimant_clinician_type <> 'doctor'))
     or (v_invite_role = 'pharmacist' and (v_claimant_role <> 'clinician' or v_claimant_clinician_type <> 'pharmacist'))
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
