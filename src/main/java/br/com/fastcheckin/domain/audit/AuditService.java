package br.com.fastcheckin.domain.audit;

import br.com.fastcheckin.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    public static final String GUEST = "GUEST";

    private static final int MAX_LIMIT = 500;

    private final AuditEntryRepository auditEntryRepository;

    public AuditService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    /**
     * Grava em transação própria: leituras rodam em transação somente leitura,
     * que não pode inserir, e o registro de auditoria não deve ser desfeito se
     * a operação que o originou falhar depois. Se a gravação falhar, a exceção
     * sobe e a operação não entrega o dado (falha fechada).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String resourceType, UUID resourceId) {
        auditEntryRepository.save(new AuditEntry(CurrentUser.name(), action, resourceType, resourceId));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> recent(UUID resourceId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT)));
        if (resourceId == null) {
            return auditEntryRepository.findAllByOrderByOccurredAtDesc(page);
        }
        return auditEntryRepository.findByResourceIdOrderByOccurredAtDesc(resourceId, page);
    }
}
