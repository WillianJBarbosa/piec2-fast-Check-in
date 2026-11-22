-- M15: FNRH criada a partir da leitura de um documento (OCR). A marca indica
-- que o OCR deixou dúvida em algum campo (ausente, inferido ou CPF inválido) e
-- que o recepcionista precisa conferir com o documento antes de confirmar.
-- A confirmação humana limpa a marca.
ALTER TABLE fnrh_registrations ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE;
