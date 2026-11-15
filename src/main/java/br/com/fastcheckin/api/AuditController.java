package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditEntry;
import br.com.fastcheckin.domain.audit.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consulta da trilha de auditoria. Somente ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record AuditResponse(
            UUID id,
            Instant occurredAt,
            String username,
            AuditAction action,
            String resourceType,
            UUID resourceId
    ) {

        static AuditResponse from(AuditEntry entry) {
            return new AuditResponse(
                    entry.getId(),
                    entry.getOccurredAt(),
                    entry.getUsername(),
                    entry.getAction(),
                    entry.getResourceType(),
                    entry.getResourceId()
            );
        }
    }

    /** Mais recentes primeiro. {@code guestId} mostra quem acessou os dados de um hóspede. */
    @GetMapping
    public List<AuditResponse> list(
            @RequestParam(required = false) UUID guestId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return auditService.recent(guestId, limit).stream().map(AuditResponse::from).toList();
    }
}
