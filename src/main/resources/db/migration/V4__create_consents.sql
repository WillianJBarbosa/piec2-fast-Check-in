-- Consentimento do hóspede por finalidade. Revogar não apaga a linha: guarda
-- a data da revogação como prova. Se o hóspede for removido, os consentimentos
-- dele vão junto (ON DELETE CASCADE); a trilha de auditoria permanece.
CREATE TABLE consents (
    id UUID PRIMARY KEY,
    guest_id UUID NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    recorded_by VARCHAR(100) NOT NULL,
    CONSTRAINT fk_consents_guest FOREIGN KEY (guest_id) REFERENCES guests (id) ON DELETE CASCADE
);

CREATE INDEX ix_consents_guest_id ON consents (guest_id);
