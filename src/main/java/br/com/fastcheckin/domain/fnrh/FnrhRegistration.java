package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.stay.Stay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "fnrh_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fnrh_stay_guest",
                columnNames = {"stay_id", "guest_id"}
        )
)
public class FnrhRegistration {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stay_id", nullable = false)
    private Stay stay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FnrhStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FnrhRegistration() {
    }

    public FnrhRegistration(Stay stay, Guest guest) {
        this.stay = stay;
        this.guest = guest;
        this.status = FnrhStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public Stay getStay() {
        return stay;
    }

    public Guest getGuest() {
        return guest;
    }

    public FnrhStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @jakarta.persistence.PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }
}
