package br.com.fastcheckin.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Registro imutável: não há métodos que alterem seus campos depois de criado. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 100, updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, length = 30, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    protected AuditEntry() {
    }

    public AuditEntry(String username, AuditAction action, String resourceType, UUID resourceId) {
        this.username = username;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getUsername() {
        return username;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
