-- PillPronto — verificare read-only a migratiilor 0001-0012 (NU o migrare de schema propriu-zisa,
-- doar SELECT — sigur de rulat oricand, de cate ori e nevoie, fara efecte secundare).
--
-- Context: migratiile din acest folder se ruleaza manual de utilizator in Supabase Dashboard ->
-- SQL Editor (vezi CLAUDE.md sectiunea 8/10) — nu exista niciun mecanism CLI care sa tina
-- evidenta automat a celor deja aplicate. Acest script inspecteaza direct catalogul Postgres
-- (pg_catalog/information_schema) pt. semnatura exacta creata de fiecare migrare (tabele,
-- coloane, functii, indecsi, politici) si raporteaza true/false per migrare.
--
-- De rulat in Supabase Dashboard -> SQL Editor. Daca o migrare iese `false`, ruleaza fisierul
-- .sql corespunzator (majoritatea sunt idempotente — re-rularea uneia deja aplicate fie nu
-- schimba nimic, fie da o eroare gen "already exists", ambele inofensive) — strict in ordine
-- numerica daca sunt mai multe de rulat (ex. 0003 inainte de 0004), pt. ca unele functii sunt
-- redefinite succesiv (claim_link: 0003 -> 0006 -> 0007, versiunea finala corecta vine doar daca
-- 0007 ruleaza ultima).

select '0001_init_schema' as migration,
  (to_regclass('public.profiles') is not null
   and to_regclass('public.patient_profiles') is not null
   and to_regclass('public.links') is not null
   and to_regclass('public.treatments') is not null
   and to_regclass('public.dose_logs') is not null
   and to_regclass('public.audit_log') is not null) as applied

union all
select '0002_rls_policies',
  (select bool_and(c.relrowsecurity) from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relname in ('profiles','patient_profiles','links','treatments','dose_logs','audit_log'))
  and exists (select 1 from pg_policies where schemaname='public' and tablename='profiles' and policyname='profiles_self')

union all
select '0003_links_open_invite',
  exists (select 1 from information_schema.columns
    where table_schema='public' and table_name='links' and column_name='grantee_user_id' and is_nullable='YES')
  and exists (select 1 from pg_trigger t join pg_class c on c.oid = t.tgrelid
    where c.relname='links' and t.tgname='links_guard_update_trigger')
  and exists (select 1 from pg_proc where proname='claim_link')

union all
select '0004_fix_links_rls_recursion',
  exists (select 1 from pg_proc where proname='is_patient_profile_owner')
  and exists (select 1 from pg_proc where proname='has_accepted_link')
  and exists (select 1 from pg_proc where proname='is_treatment_owner')
  and exists (select 1 from pg_proc where proname='has_accepted_link_for_treatment')

union all
select '0005_profiles_visible_to_linked_grantee',
  exists (select 1 from pg_proc where proname='is_linked_grantee')
  and exists (select 1 from pg_policies where schemaname='public' and tablename='profiles' and policyname='profiles_visible_to_linked_owner')

union all
select '0006_fix_links_reinvite_constraint',
  exists (select 1 from pg_indexes where schemaname='public' and indexname='links_active_patient_grantee_idx')
  and not exists (select 1 from pg_constraint where conname='links_patient_profile_id_grantee_user_id_key')

union all
select '0007_professional_invite_role_check',
  exists (select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='public' and p.proname='claim_link' and p.prosrc ilike '%different account type%')

union all
select '0008_treatment_extra_fields',
  (select count(*) from information_schema.columns
     where table_schema='public' and table_name='treatments'
       and column_name in ('forma_farmaceutica','cantitate','indicatie','instructiuni')) = 4

union all
select '0009_dose_slot_cantitate',
  exists (select 1 from information_schema.columns where table_schema='public' and table_name='treatments' and column_name='slot_cantitate_csv')
  and exists (select 1 from information_schema.columns where table_schema='public' and table_name='dose_logs' and column_name='cantitate')

union all
select '0010_treatment_cod_cim',
  exists (select 1 from information_schema.columns where table_schema='public' and table_name='treatments' and column_name='cod_cim')

union all
select '0011_gtin_mappings_catalog',
  exists (select 1 from information_schema.columns where table_schema='public' and table_name='profiles' and column_name='is_trusted_contributor')
  and to_regclass('public.gtin_mappings') is not null
  and exists (select 1 from pg_proc where proname='contribute_gtin_mapping')

union all
select '0012_treatment_expiry_date',
  exists (select 1 from information_schema.columns where table_schema='public' and table_name='treatments' and column_name='expiry_date')

order by 1;
