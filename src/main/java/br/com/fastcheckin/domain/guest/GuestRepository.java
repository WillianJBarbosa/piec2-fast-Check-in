package br.com.fastcheckin.domain.guest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    List<Guest> findByAnonymizedAtIsNull();

    /**
     * Hóspedes ainda com dados pessoais, cadastrados antes de {@code cutoff} e
     * sem nenhuma estadia que termine em {@code cutoffDate} ou depois.
     */
    @Query("""
            select g from Guest g
            where g.anonymizedAt is null
              and g.createdAt < :cutoff
              and not exists (
                select f.id from FnrhRegistration f
                where f.guest = g and f.stay.departureDate >= :cutoffDate)
            """)
    List<Guest> findExpired(@Param("cutoff") Instant cutoff, @Param("cutoffDate") LocalDate cutoffDate);
}
