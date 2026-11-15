package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.privacy.RetentionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de privacidade. Somente ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final RetentionService retentionService;

    public PrivacyController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    public record RetentionResult(int anonymizedGuests, long retentionDays) {
    }

    /** Executa a limpeza agora, sem esperar a rotina diária. */
    @PostMapping("/retention/run")
    public RetentionResult runRetention() {
        return new RetentionResult(retentionService.anonymizeExpired(), retentionService.retentionDays());
    }
}
