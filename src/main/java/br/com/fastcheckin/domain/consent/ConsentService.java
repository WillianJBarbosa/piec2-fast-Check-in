package br.com.fastcheckin.domain.consent;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditService;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final GuestRepository guestRepository;
    private final AuditService auditService;

    public ConsentService(
            ConsentRepository consentRepository,
            GuestRepository guestRepository,
            AuditService auditService
    ) {
        this.consentRepository = consentRepository;
        this.guestRepository = guestRepository;
        this.auditService = auditService;
    }

    /** Registra o consentimento; se já houver um ativo para a finalidade, devolve o existente. */
    @Transactional
    public Consent grant(UUID guestId, ConsentPurpose purpose) {
        requireGuest(guestId);
        return consentRepository.findFirstByGuestIdAndPurposeAndRevokedAtIsNull(guestId, purpose)
                .orElseGet(() -> {
                    Consent consent = consentRepository.save(new Consent(guestId, purpose, CurrentUser.name()));
                    auditService.record(AuditAction.CONSENT_GRANTED, AuditService.GUEST, guestId);
                    return consent;
                });
    }

    @Transactional
    public Consent revoke(UUID guestId, ConsentPurpose purpose) {
        requireGuest(guestId);
        Consent consent = consentRepository.findFirstByGuestIdAndPurposeAndRevokedAtIsNull(guestId, purpose)
                .orElseThrow(() -> new ResourceNotFoundException("Consentimento ativo não encontrado."));
        consent.revoke(Instant.now());
        consentRepository.flush();
        auditService.record(AuditAction.CONSENT_REVOKED, AuditService.GUEST, guestId);
        return consent;
    }

    @Transactional(readOnly = true)
    public List<Consent> list(UUID guestId) {
        requireGuest(guestId);
        return consentRepository.findByGuestIdOrderByGrantedAtAsc(guestId);
    }

    /** Barra o tratamento dos dados quando não há consentimento ativo para a finalidade. */
    @Transactional(readOnly = true)
    public void requireActive(UUID guestId, ConsentPurpose purpose) {
        if (!consentRepository.existsByGuestIdAndPurposeAndRevokedAtIsNull(guestId, purpose)) {
            throw new ConsentRequiredException(
                    "O hóspede não tem consentimento ativo para: " + purpose.getDescription() + "."
            );
        }
    }

    private void requireGuest(UUID guestId) {
        if (!guestRepository.existsById(guestId)) {
            throw new ResourceNotFoundException("Hóspede não encontrado.");
        }
    }
}
