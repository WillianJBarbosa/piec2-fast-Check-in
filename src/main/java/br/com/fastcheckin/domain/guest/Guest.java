package br.com.fastcheckin.domain.guest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guests")
public class Guest {

    @Id
    @GeneratedValue
    private UUID id;

    /** Nulo depois da anonimização por prazo de retenção. */
    @Column(name = "personal_data_ciphertext", columnDefinition = "text")
    private String personalDataCiphertext;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Guest() {
    }

    public Guest(String personalDataCiphertext) {
        this.personalDataCiphertext = personalDataCiphertext;
    }

    public void replacePersonalDataCiphertext(String newCiphertext) {
        this.personalDataCiphertext = newCiphertext;
    }

    /** Apaga os dados pessoais de forma irreversível. */
    public void anonymize(Instant at) {
        this.personalDataCiphertext = null;
        this.anonymizedAt = at;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getPersonalDataCiphertext() {
        return personalDataCiphertext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @jakarta.persistence.PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }
}
