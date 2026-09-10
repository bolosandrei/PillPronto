-- Faza 2a — cantitate diferita per ora de administrare (ex. "Nolpaza dimineata 1 comprimat,
-- seara 2 comprimate"). treatments.slot_cantitate_csv e aliniat pozitional cu times_csv (separator
-- ";" — o cantitate poate contine virgula zecimala). dose_logs.cantitate e un SNAPSHOT la
-- generare, nu legat live de treatments (vezi domain/model/DoseLog.kt) — editarea ulterioara a
-- cantitatii unui slot nu modifica istoricul deja logat.

alter table public.treatments
    add column slot_cantitate_csv text not null default '';

alter table public.dose_logs
    add column cantitate text not null default '';
