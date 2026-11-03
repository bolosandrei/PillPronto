-- Invitatie profesionala GENERICA — elimina selectia redundanta Medic/Farmacist de la
-- generarea codului (Pacientul nu mai ghiceste tipul in avans; poarta reala e deja
-- is_verified_clinician, vezi 0017/0018). Un singur rol nou 'professional' pt. invitatii noi,
-- revendicabil de ORICE cont verificat (Medic SAU Farmacist) — tipul rezultat se citeste din
-- profiles.clinician_verification_type la afisare, links.role NU se rescrie la revendicare
-- (ar cere modificarea trigger-ului links_guard_update din 0003, care blocheaza azi orice
-- schimbare de rol de catre altcineva decat Pacientul-proprietar — evitat deliberat).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0018.

alter table public.links drop constraint links_role_check;
alter table public.links add constraint links_role_check
    check (role in ('caregiver_viewer', 'caregiver_delegate', 'doctor', 'pharmacist', 'professional'));

-- claim_link — identica cu 0018, doar blocul de validare a tipului clinician se schimba in 2
-- ramuri: cod generic nou ('professional', orice tip verificat e acceptat) vs. compatibilitate
-- cu invitatii deja PENDING create inainte de aceasta migrare (role pre-ales 'doctor'/'pharmacist',
-- logica exacta din 0018, nemodificata).
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

  if v_invite_role = 'professional' then
    -- Cod generic — orice profesionist VERIFICAT (Medic SAU Farmacist) poate revendica.
    if v_claimant_verified is not true or v_claimant_verified_type is null then
      raise exception 'professional account verification required for this invite' using errcode = 'P0001';
    end if;
  elsif v_invite_role in ('doctor', 'pharmacist') then
    -- Compatibilitate cu invitatii deja PENDING, create inainte de aceasta migrare.
    if (v_invite_role = 'doctor' and (v_claimant_verified is not true or v_claimant_verified_type is distinct from 'doctor'))
       or (v_invite_role = 'pharmacist' and (v_claimant_verified is not true or v_claimant_verified_type is distinct from 'pharmacist'))
    then
      raise exception 'professional account verification required for this invite' using errcode = 'P0001';
    end if;
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
