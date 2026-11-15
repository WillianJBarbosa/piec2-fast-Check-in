-- Retenção: ao fim do prazo os dados pessoais são apagados (texto cifrado vira
-- NULL) e o hóspede fica marcado como anonimizado. A linha permanece para não
-- quebrar as FNRH e as estadias que a referenciam.
ALTER TABLE guests ALTER COLUMN personal_data_ciphertext DROP NOT NULL;
ALTER TABLE guests ADD COLUMN anonymized_at TIMESTAMP WITH TIME ZONE;
