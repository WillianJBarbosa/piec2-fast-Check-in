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

    @Column(name = "personal_data_ciphertext", nullable = false, columnDefinition = "text")
    private String personalDataCiphertext;

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
