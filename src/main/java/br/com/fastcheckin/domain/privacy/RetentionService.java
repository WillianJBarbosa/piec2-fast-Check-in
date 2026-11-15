package br.com.fastcheckin.domain.privacy;

import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditService;
import br.com.fastcheckin.domain.consent.ConsentService;
import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Política de retenção: depois de {@code app.privacy.retention-days} dias sem
 * estadia e desde o cadastro, os dados pessoais do hóspede são apagados.
 * O prazo é configurável; defina-o conforme a política do hotel e a orientação
 * jurídica, não pelo valor de partida do projeto.
 */
@Service
public class RetentionService {

    private final GuestRepository guestRepository;
    private final ConsentService consentService;
    private final AuditService auditService;
    private final long retentionDays;

    public RetentionService(
            GuestRepository guestRepository,
            ConsentService consentService,
            AuditService auditService,
            @Value("${app.privacy.retention-days:1825}") long retentionDays
    ) {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.privacy.retention-days deve ser pelo menos 1.");
        }
        this.guestRepository = guestRepository;
        this.consentService = consentService;
        this.auditService = auditService;
        this.retentionDays = retentionDays;
    }

    public long retentionDays() {
        return retentionDays;
    }

    @Transactional
    public int anonymizeExpired() {
        return anonymizeExpired(Instant.now());
    }

    /** O parâmetro {@code now} existe para os testes simularem a passagem do tempo. */
    @Transactional
    public int anonymizeExpired(Instant now) {
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        LocalDate cutoffDate = LocalDate.ofInstant(cutoff, ZoneOffset.UTC);
        List<Guest> expired = guestRepository.findExpired(cutoff, cutoffDate);
        for (Guest guest : expired) {
            guest.anonymize(now);
            consentService.revokeAll(guest.getId());
            auditService.record(AuditAction.ANONYMIZE, AuditService.GUEST, guest.getId());
        }
        guestRepository.flush();
        return expired.size();
    }
}
