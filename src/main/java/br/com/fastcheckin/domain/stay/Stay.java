package br.com.fastcheckin.domain.stay;

import br.com.fastcheckin.domain.hotel.Hotel;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stays")
public class Stay {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StayStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Stay() {
    }

    public Stay(Hotel hotel, LocalDate arrivalDate, LocalDate departureDate) {
        if (arrivalDate == null || departureDate == null || !departureDate.isAfter(arrivalDate)) {
            throw new IllegalArgumentException("A saída deve ocorrer depois da chegada.");
        }
        this.hotel = hotel;
        this.arrivalDate = arrivalDate;
        this.departureDate = departureDate;
        this.status = StayStatus.PENDING;
    }

    public void reschedule(LocalDate newArrivalDate, LocalDate newDepartureDate) {
        if (newArrivalDate == null || newDepartureDate == null || !newDepartureDate.isAfter(newArrivalDate)) {
            throw new IllegalArgumentException("A saída deve ocorrer depois da chegada.");
        }
        this.arrivalDate = newArrivalDate;
        this.departureDate = newDepartureDate;
    }

    public void changeStatus(StayStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("O status da estadia é obrigatório.");
        }
        this.status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public StayStatus getStatus() {
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
