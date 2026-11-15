-- Trilha de auditoria: guarda QUEM fez O QUE e em QUAL recurso. Nunca guarda
-- dados pessoais, só identificadores. Sem chave estrangeira de propósito: o
-- registro precisa sobreviver à remoção do hóspede.
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    username VARCHAR(100) NOT NULL,
    action VARCHAR(30) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID
);

CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_resource_id ON audit_log (resource_id);
