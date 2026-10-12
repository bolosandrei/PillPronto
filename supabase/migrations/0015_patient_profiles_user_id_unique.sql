-- PillPronto — constrangere unica pe patient_profiles.user_id (Faza 1.5h, capabilitati de cont
-- coexistente).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001-0014, ȘI STRICT DUPĂ ce a fost rulat
-- manual `supabase/tests/fix_duplicate_patient_profile.sql` (repara duplicatul real gasit
-- 2026-09-30 pe contul de test 18aec78f...) — altfel acest ALTER TABLE esueaza pe duplicatul deja
-- existent. Verifica intai (read-only, sigur de rulat oricand):
--   select user_id, count(*) from public.patient_profiles
--   where user_id is not null group by user_id having count(*) > 1;
-- Trebuie sa arate 0 randuri inainte de a continua.
--
-- Motivatie: `LocalPatientProfileProvider` genereaza un UUID local nou la fiecare
-- instalare/stergere de date a aplicatiei — fara aceasta constrangere, `ensurePatientProfileLinked`
-- poate crea un al doilea rand `patient_profiles` pentru ACELASI cont, orfan de orice legaturi
-- reale (exact bug-ul gasit live). Constrangerea nu rezolva cauza de fond (asta ramane backlog,
-- vezi CLAUDE.md sectiunea 8a — multi-profil per device), dar transforma un bug silentios intr-o
-- eroare clara de INSERT/UPSERT, detectabila imediat.
--
-- Nota: un `unique` normal pe Postgres permite oricate randuri cu user_id NULL (NULL != NULL in
-- comparatii de unicitate) — randurile "profil dependent" (owner_caregiver_id, fara user_id
-- propriu, vezi CHECK patient_profiles_exactly_one_owner din 0001) raman neafectate, fara sa fie
-- nevoie de un index partial explicit.

alter table public.patient_profiles
    add constraint patient_profiles_user_id_unique unique (user_id);
