-- Fix punctual, o singura data — NU migrare de schema (nu se numeroteaza, nu se pune in
-- supabase/migrations/). Repara cazul real gasit 2026-09-30: contul Pacient (user_id
-- 18aec78f-fac3-4765-8c1c-101875e69c78) are DOUA randuri patient_profiles:
--   - 75283f47-4dfa-40c8-a2c2-3daf9f4db362 (creat 2026-09-07) — are legatura ACCEPTATA cu
--     Apartinatorul, dar ZERO tratamente (profil vechi, orfan).
--   - da3528a8-77a9-4bd2-8631-c10d4e7f395b (creat 2026-09-29) — profilul ACTIV curent, cu
--     tratamente reale sincronizate, dar NICIO legatura de Apartinator.
-- Cauza: `ensurePatientProfileLinked` (PR #26, introdus tot pe 2026-09-29) a creat un rand nou
-- cand UUID-ul local al device-ului nu s-a potrivit cu niciun rand existent — dar patient_profiles
-- nu are (inca) o constrangere unica pe user_id, deci n-a detectat ca acel user avea deja un profil.
--
-- Fix: muta legaturile (si eventualele randuri audit_log) de pe profilul vechi pe cel activ, apoi
-- sterge profilul vechi orfan (0 tratamente, deci fara pierdere de date).
--
-- Sigur: totul intr-o tranzactie — daca ceva nu corespunde asteptarilor (ex. profilul vechi are
-- totusi tratamente intre timp), COMMIT-ul nu ruleaza automat, verifica rezultatele intermediare
-- inainte de a decomenta linia finala `commit;`.

begin;

-- links_guard_update_trigger (migrarea 0003) face patient_profile_id IMUABIL pe UPDATE — hardening
-- deliberat, ca sa nu poata un grantee sa-si redirectioneze singur legatura catre alt pacient prin
-- RLS. Corect pentru clienti, dar blocheaza si acest fix administrativ de date — dezactivat STRICT
-- pentru durata acestei tranzactii, reactivat inainte de commit (mai jos).
alter table public.links disable trigger links_guard_update_trigger;

update public.links
set patient_profile_id = 'da3528a8-77a9-4bd2-8631-c10d4e7f395b'
where patient_profile_id = '75283f47-4dfa-40c8-a2c2-3daf9f4db362';

alter table public.links enable trigger links_guard_update_trigger;

update public.audit_log
set patient_profile_id = 'da3528a8-77a9-4bd2-8631-c10d4e7f395b'
where patient_profile_id = '75283f47-4dfa-40c8-a2c2-3daf9f4db362';

-- Verificare inainte de a sterge profilul vechi — trebuie sa arate 0.
select count(*) as should_be_zero_treatments
from public.treatments
where patient_profile_id = '75283f47-4dfa-40c8-a2c2-3daf9f4db362';

delete from public.patient_profiles
where id = '75283f47-4dfa-40c8-a2c2-3daf9f4db362';

-- Verificare finala — legatura acceptata trebuie sa arate acum spre profilul activ.
select id, status, patient_profile_id, grantee_user_id
from public.links
where grantee_user_id = 'b77ad474-2257-43de-9cd9-70c7cb6b0db9';

commit;
