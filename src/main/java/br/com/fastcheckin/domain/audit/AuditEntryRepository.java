package br.com.fastcheckin.domain.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditEntry> findByResourceIdOrderByOccurredAtDesc(UUID resourceId, Pageable pageable);
}
