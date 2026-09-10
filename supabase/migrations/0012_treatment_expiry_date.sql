-- Faza 2b-i (extensie) — data expirarii ULTIMEI cutii scanate pt. un tratament (AI 17 din codul
-- GS1 DataMatrix serializat FMD, vezi domain/gs1/Gs1Parser.kt + eu2016161/gs1fmd2016 in
-- thesis.bib). Optional, text (ISO date), default null — aditiv. Folosita pt. afisare la scanare +
-- alerte locale de expirare apropiata/depasita (ExpiryAlertWorker) — nu e istoric per-cutie, o
-- rescanare o suprascrie.

alter table public.treatments
    add column expiry_date text;
