-- Verificare finala, read-only: mai exista alte conturi cu 2+ randuri patient_profiles?
-- Trebuie sa arate 0 randuri ca precondictia 2 din planul Fazei 1.5h sa fie bifata.
select
    pp.user_id,
    count(*) as profile_count,
    array_agg(pp.id order by pp.created_at) as patient_profile_ids
from public.patient_profiles pp
where pp.user_id is not null
group by pp.user_id
having count(*) > 1;
