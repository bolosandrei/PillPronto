-- Revizori de verificare profesionala — alti utilizatori (nu doar dezvoltatorul) pot aproba/
-- respinge cereri de verificare, desemnati tot MANUAL (flag nou, mirror exact
-- is_trusted_contributor din 0011 — zero cale de scriere din app, setat direct SQL).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0019.

alter table public.profiles
    add column is_verification_reviewer boolean not null default false,
    add column clinician_verification_rejected boolean not null default false;

-- FIX critic (TODO deja semnalat in comentariul din 0017): trigger-ul verifica azi
-- `session_user <> 'postgres'` — dar o functie SECURITY DEFINER apelata de un reviewer
-- autentificat schimba `current_user` (la owner-ul functiei, 'postgres'), NU `session_user`
-- (ramane rolul original de conectare prin PostgREST, niciodata 'postgres'). Cu verificarea
-- veche, ORICE aprobare prin RPC ar fi anulata silentios de acest trigger. Sesiunile SQL Editor
-- directe raman `current_user = 'postgres'` identic azi — comportamentul manual existent nu se
-- schimba.
create or replace function public.protect_is_verified_clinician()
returns trigger
language plpgsql
as $$
begin
    if new.is_verified_clinician is distinct from old.is_verified_clinician
       and new.is_verified_clinician = true
       and current_user <> 'postgres'
    then
        new.is_verified_clinician := old.is_verified_clinician;
    end if;
    return new;
end;
$$;

-- Pattern identic claim_link/contribute_gtin_mapping: SECURITY DEFINER, verifica auth.uid()
-- intern, fara nicio politica RLS noua pe profiles.
create or replace function public.list_pending_clinician_verifications()
returns table (
    user_id uuid,
    display_name text,
    cuim text,
    full_name text,
    verification_type text
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_is_reviewer boolean;
begin
  select is_verification_reviewer into v_is_reviewer from public.profiles where id = auth.uid();
  if v_is_reviewer is not true then
    raise exception 'not authorized to review verification requests' using errcode = 'P0001';
  end if;

  return query
    select p.id, p.display_name, p.clinician_verification_cuim, p.clinician_verification_full_name,
           p.clinician_verification_type
      from public.profiles p
     where p.clinician_verification_type is not null
       and p.is_verified_clinician = false
       and p.clinician_verification_rejected = false;
end;
$$;

create or replace function public.approve_clinician_verification(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_is_reviewer boolean;
begin
  select is_verification_reviewer into v_is_reviewer from public.profiles where id = auth.uid();
  if v_is_reviewer is not true then
    raise exception 'not authorized to review verification requests' using errcode = 'P0001';
  end if;

  update public.profiles
     set is_verified_clinician = true,
         clinician_verification_rejected = false
   where id = p_user_id;
end;
$$;

create or replace function public.reject_clinician_verification(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_is_reviewer boolean;
begin
  select is_verification_reviewer into v_is_reviewer from public.profiles where id = auth.uid();
  if v_is_reviewer is not true then
    raise exception 'not authorized to review verification requests' using errcode = 'P0001';
  end if;

  update public.profiles
     set is_verified_clinician = false,
         clinician_verification_rejected = true
   where id = p_user_id;
end;
$$;

revoke all on function public.list_pending_clinician_verifications() from public;
grant execute on function public.list_pending_clinician_verifications() to authenticated;
revoke all on function public.approve_clinician_verification(uuid) from public;
grant execute on function public.approve_clinician_verification(uuid) to authenticated;
revoke all on function public.reject_clinician_verification(uuid) from public;
grant execute on function public.reject_clinician_verification(uuid) to authenticated;
