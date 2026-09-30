-- PillPronto — teste RLS adversariale (Faza 1.5g), pgTAP.
--
-- De rulat manual in Supabase Dashboard -> SQL Editor, DUPA toate migrarile (0001-0014, in
-- ordine). NU e o migrare de schema — re-rulabil oricand ca verificare, nu doar o data.
--
-- Sigur de rulat: tot scriptul e o singura tranzactie cu ROLLBACK explicit la final — fixturile
-- (useri/profiluri/tratamente sintetice) NU raman in baza de date, indiferent daca toate testele
-- trec sau nu. Daca vreo instructiune da eroare, Postgres anuleaza automat toata tranzactia
-- (nimic ramane pe jumatate aplicat).
--
-- Nota: insert-ul minimal in auth.users (doar id+email) e pattern-ul standard documentat pt.
-- testarea RLS Supabase cu pgTAP (impersonare via `request.jwt.claims`, vezi functia
-- `pg_temp.authenticate_as` de mai jos) — daca schema auth a acestui proiect specific cere
-- coloane suplimentare NOT NULL, primul INSERT va da eroare clara si tranzactia se anuleaza
-- curat, fara nicio urma.

begin;

create extension if not exists pgtap with schema extensions;
set search_path = public, extensions;

select plan(8);

-- Colecteaza toate liniile TAP (plan/ok/not ok/diag/finish) intr-un singur tabel temporar, ca
-- editorul SQL (care afiseaza de regula doar rezultatul ULTIMEI instructiuni) sa poata arata
-- tot rezultatul testelor dintr-un singur SELECT final, in ordine.
create temp table test_output (id serial primary key, line text);
-- Testele comuta la rolul `authenticated` (pg_temp.authenticate_as) — fara acest grant, insert-urile
-- de mai jos ar da "permission denied for table test_output" odata ce rolul curent nu mai e cel
-- care a creat tabelul temporar.
grant insert, select on test_output to authenticated;
grant usage, select on sequence test_output_id_seq to authenticated;

-- ============================================================================
-- Fixturi sintetice: Pacient A (owner b...01) + Pacient B (owner b...02, complet neinrudit) +
-- 3 Apartinatori cu legaturi diferite catre Pacientul A (acceptata / revocata / doar pending).
-- UUID-uri sintetice, doar cifre hexazecimale valide (0-9, a).
-- ============================================================================

insert into auth.users (id, email) values
    ('a0000000-0000-0000-0000-000000000001', 'pgtap-patient-a@pgtap.local'),
    ('a0000000-0000-0000-0000-000000000002', 'pgtap-patient-b@pgtap.local'),
    ('a0000000-0000-0000-0000-000000000011', 'pgtap-caregiver-accepted@pgtap.local'),
    ('a0000000-0000-0000-0000-000000000012', 'pgtap-caregiver-revoked@pgtap.local'),
    ('a0000000-0000-0000-0000-000000000013', 'pgtap-caregiver-pending@pgtap.local');

insert into public.profiles (id, role, display_name) values
    ('a0000000-0000-0000-0000-000000000001', 'patient', 'pgtap Pacient A'),
    ('a0000000-0000-0000-0000-000000000002', 'patient', 'pgtap Pacient B'),
    ('a0000000-0000-0000-0000-000000000011', 'caregiver', 'pgtap Apartinator Acceptat'),
    ('a0000000-0000-0000-0000-000000000012', 'caregiver', 'pgtap Apartinator Revocat'),
    ('a0000000-0000-0000-0000-000000000013', 'caregiver', 'pgtap Apartinator Pending');

insert into public.patient_profiles (id, display_name, user_id) values
    ('b0000000-0000-0000-0000-000000000001', 'pgtap Pacient A', 'a0000000-0000-0000-0000-000000000001'),
    ('b0000000-0000-0000-0000-000000000002', 'pgtap Pacient B', 'a0000000-0000-0000-0000-000000000002');

insert into public.links (patient_profile_id, grantee_user_id, role, status, invite_code) values
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000011', 'caregiver_viewer', 'accepted', 'PGTAP0001'),
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000012', 'caregiver_viewer', 'revoked', 'PGTAP0002'),
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000013', 'caregiver_viewer', 'pending', 'PGTAP0003');

insert into public.treatments (id, patient_profile_id, medication_name, dosage, start_date) values
    ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'pgtap Paracetamol', '500mg', current_date);

insert into public.dose_logs (treatment_id, scheduled_at, status) values
    ('c0000000-0000-0000-0000-000000000001', now(), 'PENDING');

-- ============================================================================
-- Helper de impersonare — SET ROLE authenticated + request.jwt.claims (de unde citeste
-- auth.uid() real). SET LOCAL, deci efectul dureaza pana la finalul tranzactiei curente
-- (ROLLBACK de la capatul scriptului), nu se scurge in afara acestui script.
-- ============================================================================
create or replace function pg_temp.authenticate_as(p_user_id uuid) returns void as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_user_id::text)::text, true);
    set local role authenticated;
end;
$$ language plpgsql;

-- 1. Control pozitiv — Apartinator cu legatura ACCEPTATA VEDE tratamentele (confirma ca
--    fixturile + RLS-ul normal functioneaza inainte sa avem incredere in testele negative).
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000011');
insert into test_output(line) select is(
    (select count(*)::int from public.treatments where patient_profile_id = 'b0000000-0000-0000-0000-000000000001'),
    1,
    'Apartinator cu legatura ACCEPTATA vede tratamentul pacientului (control pozitiv)'
);

-- 2. Apartinator cu legatura REVOCATA -> SELECT treatments -> 0 randuri.
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000012');
insert into test_output(line) select is(
    (select count(*)::int from public.treatments where patient_profile_id = 'b0000000-0000-0000-0000-000000000001'),
    0,
    'Apartinator cu legatura REVOCATA NU vede tratamentele pacientului'
);

-- 3. Apartinator cu legatura doar PENDING (neacceptata) -> SELECT treatments -> 0 randuri.
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000013');
insert into test_output(line) select is(
    (select count(*)::int from public.treatments where patient_profile_id = 'b0000000-0000-0000-0000-000000000001'),
    0,
    'Apartinator cu legatura PENDING (neacceptata) NU vede tratamentele pacientului'
);

-- 4. Apartinator cu legatura REVOCATA -> UPDATE pe treatments -> 0 randuri afectate, deloc scris.
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000012');
update public.treatments set dosage = 'HACKED' where id = 'c0000000-0000-0000-0000-000000000001';
-- Verificarea trebuie facuta cu un actor care POATE vedea tratamentul (Pacientul A, owner) — daca
-- am ramane autentificati ca apartinatorul revocat, RLS SELECT il blocheaza si pe el, iar
-- verificarea ar primi NULL (0 randuri) in loc de valoarea reala, indiferent daca UPDATE-ul a
-- reusit sau nu (fals negativ, nu o dovada ca RLS a blocat scrierea).
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000001');
insert into test_output(line) select is(
    (select dosage from public.treatments where id = 'c0000000-0000-0000-0000-000000000001'),
    '500mg',
    'Apartinator revocat NU poate modifica tratamentul (RLS treatments_owner_all)'
);

-- 5. Control pozitiv — Apartinator ACCEPTAT poate insera un rand de audit legitim pt. Pacientul A
--    (confirma ca hardening-ul din migrarea 0014 NU blocheaza cazul legitim).
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000011');
insert into test_output(line) select lives_ok(
    $$ insert into public.audit_log (actor_user_id, patient_profile_id, action, entity)
       values ('a0000000-0000-0000-0000-000000000011', 'b0000000-0000-0000-0000-000000000001', 'view', 'patient_data') $$,
    'Apartinator ACCEPTAT poate insera audit_log legitim pt. pacientul lui (migrarea 0014, caz pozitiv)'
);

-- 6. Bug-ul real gasit prin research (fixat in migrarea 0014) — Pacientul B, FARA nicio legatura
--    cu Pacientul A, incearca sa insereze un rand de audit FABRICAT pt. Pacientul A -> trebuie
--    respins de RLS.
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000002');
-- throws_ok(sql, errcode) cu 2 argumente foloseste al 2-lea ca SQLSTATE asteptat (descriere auto).
-- throws_ok(sql, errcode, X) cu 3 argumente foloseste X ca MESAJ de eroare asteptat, NU ca
-- descriere — de-aia rularea anterioara arata "wanted: 42501: <descrierea noastra>" comparat gresit
-- cu mesajul real Postgres. Fix: forma cu 4 argumente — errcode, NULL (nu verificam mesajul exact,
-- ca sa nu cuplam testul de formularea exacta Postgres, posibil sa difere intre versiuni), apoi
-- descrierea pe a 4-a pozitie.
insert into test_output(line) select throws_ok(
    $$ insert into public.audit_log (actor_user_id, patient_profile_id, action, entity)
       values ('a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'view', 'patient_data') $$,
    '42501',
    null,
    'Insert de audit_log FABRICAT, fara nicio legatura reala, e respins de RLS (migrarea 0014)'
);

-- 7. Pacientul B -> SELECT pe audit_log-ul Pacientului A -> 0 randuri (nu vede istoricul altcuiva).
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000002');
insert into test_output(line) select is(
    (select count(*)::int from public.audit_log where patient_profile_id = 'b0000000-0000-0000-0000-000000000001'),
    0,
    'Pacientul B NU vede trail-ul de audit al Pacientului A'
);

-- 8. Control pozitiv — Pacientul A (owner) VEDE randul de audit legitim inserat la pasul 5.
select pg_temp.authenticate_as('a0000000-0000-0000-0000-000000000001');
insert into test_output(line) select is(
    (select count(*)::int from public.audit_log where patient_profile_id = 'b0000000-0000-0000-0000-000000000001'),
    1,
    'Pacientul A vede propriul trail de audit (control pozitiv)'
);

insert into test_output(line) select * from finish();

-- Unicul rezultat vizibil in SQL Editor: toate liniile TAP (plan/ok/not ok/diag/finish), in ordine
-- — foloseste asta ca sa vezi exact care teste au picat, nu doar ultimul rand de sumar.
select line from test_output order by id;

rollback;
