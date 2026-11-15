package br.com.fastcheckin.domain.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "guest_id", nullable = false, updatable = false)
    private UUID guestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private ConsentPurpose purpose;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "recorded_by", nullable = false, length = 100, updatable = false)
    private String recordedBy;

    protected Consent() {
    }

    public Consent(UUID guestId, ConsentPurpose purpose, String recordedBy) {
        this.guestId = guestId;
        this.purpose = purpose;
        this.recordedBy = recordedBy;
        this.grantedAt = Instant.now();
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
