-- PillPronto — Pacientul vede numele Apartinatorilor legati (Faza 1.5d, rafinare UX dupa
-- testarea pe device: ecranul "Gestionează accesul Aparținătorilor" arata azi doar statusul
-- generic "Acces acordat", fara identitatea Apartinatorului).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001-0004.
--
-- Motivatie tehnica: `profiles_self` (0001) permite unui user sa-si vada DOAR propriul rand.
-- Pacientul n-are azi nicio cale sa citeasca `profiles.display_name` al unui Apartinator legat.
-- Solutie: aceeasi tehnica SECURITY DEFINER folosita in 0004 (nu subquery corelat direct in
-- politica) — desi niciun tabel implicat aici nu subqueriaza `profiles` azi (deci n-ar exista
-- risc de recursivitate nici cu un subquery simplu), pastram stilul consecvent.

create or replace function public.is_linked_grantee(p_grantee_user_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.links l
        join public.patient_profiles p on p.id = l.patient_profile_id
        where l.grantee_user_id = p_grantee_user_id
          and l.status = 'accepted'
          and (p.user_id = auth.uid() or p.owner_caregiver_id = auth.uid())
    );
$$;

revoke all on function public.is_linked_grantee(uuid) from public;
grant execute on function public.is_linked_grantee(uuid) to authenticated;

create policy profiles_visible_to_linked_owner on public.profiles
    for select
    using (public.is_linked_grantee(id));
