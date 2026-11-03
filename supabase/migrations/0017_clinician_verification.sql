-- Verificare profesionala Medic/Farmacist prin cerere + aprobare manuala (inlocuieste
-- auto-declararea neverificata clinician_type ca poarta de revendicare, vezi 0018).
--
-- De rulat in Supabase Dashboard -> SQL Editor, DUPA 0016.
--
-- Userul completeaza o cerere (CUIM + tip + nume complet) din app; DOAR aprobarea manuala a
-- dezvoltatorului (direct SQL, ca la is_trusted_contributor din 0011) seteaza
-- is_verified_clinician = true. Coloana veche profiles.clinician_type NU se sterge (politica
-- proiectului: nicio migrare anterioara nu a facut DROP COLUMN) -- ramane neutilizata de app,
-- utila ca istoric la aprobarea manuala.

alter table public.profiles
    add column clinician_verification_cuim text,
    add column clinician_verification_full_name text,
    add column clinician_verification_type text
        check (clinician_verification_type in ('doctor', 'pharmacist')),
    add column is_verified_clinician boolean not null default false;

-- Protectie: is_verified_clinician e acum POARTA REALA de acces (claim_link, 0018) -- spre
-- deosebire de is_trusted_contributor (gateaza doar scrierea in catalogul GTIN, fara protectie
-- explicita, gap preexistent, nereparat aici). Fara acest trigger, un user ar putea seta singur
-- flagul pe true printr-un PATCH direct catre PostgREST (RLS e doar row-level, nu column-level),
-- bypassand complet aprobarea manuala.
--
-- session_user = 'postgres' e presupunerea pt. sesiunile din Supabase Dashboard SQL Editor
-- (fluxul de aprobare manuala existent). De verificat la prima aprobare reala: `select
-- session_user;` in SQL Editor -- daca nu e 'postgres', update-ul de aprobare n-ar avea efect
-- vizibil (trigger-ul l-ar anula silentios), si trigger-ul trebuie ajustat la rolul real.
create or replace function public.protect_is_verified_clinician()
returns trigger
language plpgsql
as $$
begin
    if new.is_verified_clinician is distinct from old.is_verified_clinician
       and new.is_verified_clinician = true
       and session_user <> 'postgres'
    then
        new.is_verified_clinician := old.is_verified_clinician;
    end if;
    return new;
end;
$$;

create trigger protect_is_verified_clinician
    before update on public.profiles
    for each row
    execute function public.protect_is_verified_clinician();
