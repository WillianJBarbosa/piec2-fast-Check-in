package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.domain.facial.FacialResult;
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

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    /** Resultado da última comparação facial; nulo enquanto nenhuma foi feita. Não guarda imagem nem vetor facial. */
    @Enumerated(EnumType.STRING)
    @Column(name = "facial_result", length = 20)
    private FacialResult facialResult;

    @Column(name = "facial_checked_at")
    private Instant facialCheckedAt;

    /** O recepcionista conferiu a identidade pessoalmente, mesmo sem a comparação ser "compativel". */
    @Column(name = "facial_confirmed_manually", nullable = false)
    private boolean facialConfirmedManually;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FnrhRegistration() {
    }

    public FnrhRegistration(Stay stay, Guest guest) {
        this(stay, guest, false);
    }

    /** @param reviewRequired true quando o OCR deixou dúvida e o recepcionista precisa conferir os campos */
    public FnrhRegistration(Stay stay, Guest guest, boolean reviewRequired) {
        this.stay = stay;
        this.guest = guest;
        this.status = FnrhStatus.PENDING;
        this.reviewRequired = reviewRequired;
    }

    public void changeStatus(FnrhStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("O status da FNRH é obrigatório.");
        }
        this.status = newStatus;
        if (newStatus == FnrhStatus.CONFIRMED) {
            // a confirmação é a conferência humana: limpa a marca de revisão
            this.reviewRequired = false;
        }
    }

    /** Registra uma nova comparação; uma confirmação manual anterior deixa de valer. */
    public void recordFacialCheck(FacialResult result) {
        if (result == null) {
            throw new IllegalArgumentException("O resultado da comparação facial é obrigatório.");
        }
        this.facialResult = result;
        this.facialCheckedAt = Instant.now();
        this.facialConfirmedManually = false;
    }

    public boolean hasFacialCheck() {
        return facialResult != null;
    }

    public void confirmFacialManually() {
        this.facialConfirmedManually = true;
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

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public FacialResult getFacialResult() {
        return facialResult;
    }

    public Instant getFacialCheckedAt() {
        return facialCheckedAt;
    }

    public boolean isFacialConfirmedManually() {
        return facialConfirmedManually;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @jakarta.persistence.PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }
}
