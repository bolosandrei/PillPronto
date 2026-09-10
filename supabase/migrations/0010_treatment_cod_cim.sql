-- Faza 2b-i — trasabilitate la intrarea exacta din Nomenclatorul ANMDMR aleasa pt. un tratament
-- (manual, cautare text, sau confirmata printr-un scan GS1 DataMatrix reusit). Optional, text,
-- default '' — aditiv. NU contine GTIN-ul propriu-zis: maparea GTIN->Cod CIM (tabelul local
-- gtin_mappings, Room) ramane STRICT locala, nu se sincronizeaza (specifica exemplarului fizic
-- scanat pe acel device, nu date de sanatate portabile intre device-uri).

alter table public.treatments
    add column cod_cim text not null default '';
