-- Faza 2b-i (extensie) — catalog partajat de mapari GTIN->Cod CIM, construit colaborativ de
-- contribuitori de incredere, tras (pull) de TOTI userii, inclusiv neautentificati. Primul tabel
-- cu adevarat public din aceasta schema (toate celelalte sunt scopate pe rand propriu sau pe un
-- lant de proprietate patient_profile_id/links) — vezi CLAUDE.md pt. rationament complet.

-- Flag de incredere, ortogonal la `role`/`clinician_type` — NU un rol nou, un capability-flag
-- restrans strict la "poate contribui la gtin_mappings". Setat manual (SQL direct), NU prin
-- auto-declarare (spre deosebire de clinician_type='pharmacist'/'doctor', care sunt auto-declarate
-- si neverificate — vezi CLAUDE.md, flag-ul "neverificat" din UI).
alter table public.profiles
    add column is_trusted_contributor boolean not null default false;

create table public.gtin_mappings (
    gtin text primary key,
    cod_cim text not null,
    contributor_id uuid references auth.users(id) on delete set null,
    confirmed_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.gtin_mappings enable row level security;

-- SELECT deschis catre oricine, inclusiv neautentificat (`anon`) — nu e date de sanatate, e date
-- de produs public (GTIN -> Cod CIM ANMDMR), la fel ca Nomenclatorul. Pastreaza promisiunea
-- "aplicatia ramane functionala fara login" — chiar si un user neautentificat beneficiaza de
-- catalogul comun.
create policy gtin_mappings_public_read
    on public.gtin_mappings for select
    to anon, authenticated
    using (true);

-- Deliberat NICIO politica INSERT/UPDATE/DELETE -> refuzate implicit pt. toata lumea, inclusiv
-- prin REST direct. Singura cale de scriere e functia SECURITY DEFINER de mai jos.

-- Pattern identic cu claim_link (0003/0007): functia citeste profiles.is_trusted_contributor al
-- apelantului prin auth.uid() (ocolind RLS intern, fara risc de recursivitate — pattern-ul din
-- 0004), respinge daca nu e true, altfel scrie. Clientul nu poate seta contributor_id sau ocoli
-- verificarea — totul hardcodat server-side. Deoarece doar contribuitori de incredere pot scrie
-- (verificat server-side), orice scriere reusita e prin definitie de incredere -> last-write-wins
-- pe conflict e suficient, fara arbitraj complex.
create or replace function public.contribute_gtin_mapping(p_gtin text, p_cod_cim text)
returns public.gtin_mappings
language plpgsql
security definer
set search_path = public
as $$
declare
  v_is_trusted boolean;
  v_row public.gtin_mappings;
begin
  select is_trusted_contributor into v_is_trusted
    from public.profiles
   where id = auth.uid();

  if v_is_trusted is not true then
    raise exception 'not authorized to contribute gtin mappings' using errcode = 'P0001';
  end if;

  if p_gtin is null or length(p_gtin) = 0 or p_cod_cim is null or length(p_cod_cim) = 0 then
    raise exception 'gtin and cod_cim are required' using errcode = 'P0001';
  end if;

  insert into public.gtin_mappings (gtin, cod_cim, contributor_id, confirmed_at, updated_at)
  values (p_gtin, p_cod_cim, auth.uid(), now(), now())
  on conflict (gtin) do update
    set cod_cim = excluded.cod_cim,
        contributor_id = excluded.contributor_id,
        updated_at = now()
  returning * into v_row;

  return v_row;
end;
$$;

revoke all on function public.contribute_gtin_mapping(text, text) from public;
grant execute on function public.contribute_gtin_mapping(text, text) to authenticated;
