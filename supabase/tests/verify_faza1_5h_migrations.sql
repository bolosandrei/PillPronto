-- Verificare read-only, sigura de rulat oricand — confirma ca migrarile 0015+0016 chiar au avut
-- efect (nu doar "s-a rulat fara eroare").

-- 1. Coloana `role` trebuie sa fi disparut din profiles.
select not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'profiles' and column_name = 'role'
) as role_column_dropped;

-- 2. Constrangerea unica pe patient_profiles.user_id trebuie sa existe.
select exists (
    select 1 from pg_constraint where conname = 'patient_profiles_user_id_unique'
) as unique_constraint_exists;

-- 3. claim_link trebuie sa fie versiunea noua (fara referinte la "clinician'" / rol de cont) —
--    cauta un fragment specific noii implementari.
select prosrc ilike '%complete your profile before claiming an invite%' as claim_link_updated
from pg_proc where proname = 'claim_link';
