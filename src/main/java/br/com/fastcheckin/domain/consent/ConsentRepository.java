package br.com.fastcheckin.domain.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    Optional<Consent> findFirstByGuestIdAndPurposeAndRevokedAtIsNull(UUID guestId, ConsentPurpose purpose);

    boolean existsByGuestIdAndPurposeAndRevokedAtIsNull(UUID guestId, ConsentPurpose purpose);

    List<Consent> findByGuestIdOrderByGrantedAtAsc(UUID guestId);

    List<Consent> findByGuestIdAndRevokedAtIsNull(UUID guestId);
}
