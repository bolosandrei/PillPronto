-- PillPronto — permite re-invitarea aceluiasi Apartinator dupa revocare (bug real, gasit la
-- testarea pe device: scanarea unui cod QR proaspat pentru un Apartinator anterior
-- revocat/testat esua cu "duplicate key value violates unique constraint
-- links_patient_profile_id_grantee_user_id_key", cod Postgres 23505).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001-0005.
--
-- Cauza: `unique (patient_profile_id, grantee_user_id)` din 0001_init_schema.sql e un constraint
-- global — se aplica si randurilor `revoked`. Cand un Pacient genereaza o invitatie noua pentru
-- un Apartinator cu care a mai avut o legatura (chiar revocata), `claim_link` (0003) incearca sa
-- seteze grantee_user_id pe randul nou `pending`, ceea ce coliziona cu vechiul rand `revoked` —
-- desi acela nu mai e activ. Fix: constraint-ul devine partial, aplicat doar randurilor
-- nerevocate — o singura legatura ACTIVA (pending/accepted) per pereche pacient-apartinator,
-- dar istoricul revocat nu mai blocheaza o reinvitare.

alter table public.links drop constraint links_patient_profile_id_grantee_user_id_key;

create unique index links_active_patient_grantee_idx
    on public.links (patient_profile_id, grantee_user_id)
    where status <> 'revoked';

-- Mesaj mai clar pentru cazul ramas legitim (Apartinatorul incearca sa revendice un al doilea cod
-- pending cat timp are deja o legatura activa cu acelasi pacient) — inainte aparea ca eroare bruta
-- Postgres (23505), acum devine acelasi tip de eroare P0001 ca "cod invalid/deja folosit".
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
exception
  when unique_violation then
    raise exception 'you already have an active link to this patient' using errcode = 'P0001';
end;
$$;
