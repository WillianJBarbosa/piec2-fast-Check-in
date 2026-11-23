-- M17: comparação facial entre a foto do documento e a selfie do hóspede.
-- Guarda só o resultado da comparação (compativel, revisar ou diferente) e quando
-- ela foi feita. Nenhuma imagem nem vetor facial é gravado. Quando o resultado
-- não é "compativel", o recepcionista pode confirmar a identidade manualmente.
ALTER TABLE fnrh_registrations ADD COLUMN facial_result VARCHAR(20);
ALTER TABLE fnrh_registrations ADD COLUMN facial_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE fnrh_registrations ADD COLUMN facial_confirmed_manually BOOLEAN NOT NULL DEFAULT FALSE;
