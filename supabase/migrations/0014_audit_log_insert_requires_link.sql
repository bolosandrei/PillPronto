-- PillPronto — strange politica de INSERT pe `audit_log` (Faza 1.5f).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0001-0013.
--
-- Bug real de securitate gasit prin research (nu testare), 2026-09-29, chiar inaintea implementarii
-- trail-ului de audit: politica originala `audit_log_insert` (0002_rls_policies.sql) verifica
-- DOAR `actor_user_id = auth.uid()` — orice user autentificat putea insera un rand de audit
-- FABRICAT pt. orice `patient_profile_id`, indiferent daca are vreo legatura reala cu acel
-- pacient. Nu expune date (audit_log nu contine continut clinic), dar submineaza increderea in
-- exact feature-ul care exista ca sa fie de incredere ("cine imi vede datele").
--
-- Fix: refoloseste `has_accepted_link` (functie SECURITY DEFINER deja existenta, din
-- 0004_fix_links_rls_recursion.sql) — actorul trebuie sa aiba o legatura ACCEPTATA cu profilul de
-- pacient pt. care insereaza randul de audit.

drop policy if exists audit_log_insert on public.audit_log;
create policy audit_log_insert on public.audit_log
    for insert
    with check (actor_user_id = auth.uid() and public.has_accepted_link(patient_profile_id));
