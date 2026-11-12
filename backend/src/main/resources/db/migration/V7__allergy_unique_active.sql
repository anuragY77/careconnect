-- A patient has at most one ACTIVE allergy per substance, whatever the letter case. Two screens adding the
-- same allergy at the same moment cannot both succeed. Inactive and entered-in-error rows are history and
-- may repeat.
CREATE UNIQUE INDEX uq_allergy_active_substance ON allergy (patient_id, lower(substance)) WHERE status = 'ACTIVE';
