package br.com.fastcheckin.domain.audit;

public enum AuditAction {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    CONSENT_GRANTED,
    CONSENT_REVOKED,
    ANONYMIZE
}
