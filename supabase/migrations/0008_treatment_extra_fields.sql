-- Faza 2a — campuri optionale noi pe treatments, inspirate din aplicatii publice de referinta
-- (Medisafe, MyTherapy): forma farmaceutica (pre-completata din Nomenclator ANMDMR la potrivire),
-- cantitate per doza (separata de dosage/concentratie), indicatie/motiv tratament, instructiuni
-- (text liber). Toate optionale, text, default '' — aditiv, nu afecteaza randurile existente.

alter table public.treatments
    add column forma_farmaceutica text not null default '',
    add column cantitate text not null default '',
    add column indicatie text not null default '',
    add column instructiuni text not null default '';
