select actor_user_id, patient_profile_id, occurred_at
from public.audit_log
where occurred_at > now() - interval '10 minutes'
order by occurred_at desc;
