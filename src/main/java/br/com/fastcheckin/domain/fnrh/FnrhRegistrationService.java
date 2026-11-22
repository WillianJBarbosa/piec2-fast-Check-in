package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditService;
import br.com.fastcheckin.domain.consent.ConsentPurpose;
import br.com.fastcheckin.domain.consent.ConsentService;
import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.guest.GuestService;
import br.com.fastcheckin.domain.stay.Stay;
import br.com.fastcheckin.domain.stay.StayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FnrhRegistrationService {

    private final FnrhRegistrationRepository fnrhRegistrationRepository;
    private final StayRepository stayRepository;
    private final GuestRepository guestRepository;
    private final ConsentService consentService;
    private final GuestService guestService;
    private final AuditService auditService;

    public FnrhRegistrationService(
            FnrhRegistrationRepository fnrhRegistrationRepository,
            StayRepository stayRepository,
            GuestRepository guestRepository,
            ConsentService consentService,
            GuestService guestService,
            AuditService auditService
    ) {
        this.fnrhRegistrationRepository = fnrhRegistrationRepository;
        this.stayRepository = stayRepository;
        this.guestRepository = guestRepository;
        this.consentService = consentService;
        this.guestService = guestService;
        this.auditService = auditService;
    }

    @Transactional
    public FnrhRegistration register(UUID stayId, UUID guestId) {
        Stay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new ResourceNotFoundException("Estadia não encontrada."));
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóspede não encontrado."));
        consentService.requireActive(guestId, ConsentPurpose.CHECKIN_FNRH);
        return fnrhRegistrationRepository.saveAndFlush(new FnrhRegistration(stay, guest));
    }

    /**
     * Cadastra o hóspede lido do documento (com o consentimento de CHECKIN_FNRH)
     * e cria a FNRH pendente na mesma transação: ou grava tudo, ou nada. A
     * leitura do OCR acontece antes, fora de transação.
     */
    @Transactional
    public FnrhRegistration registerFromDocument(UUID stayId, GuestPersonalData personalData, boolean reviewRequired) {
        Stay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new ResourceNotFoundException("Estadia não encontrada."));
        Guest guest = guestService.register(personalData);
        FnrhRegistration registration = fnrhRegistrationRepository.saveAndFlush(
                new FnrhRegistration(stay, guest, reviewRequired));
        auditService.record(AuditAction.CREATE, AuditService.FNRH, registration.getId());
        return registration;
    }

    /** Confirmação feita pelo recepcionista depois de conferir os dados; limpa a marca de revisão. */
    @Transactional
    public FnrhRegistration confirm(UUID id) {
        FnrhRegistration registration = updateStatus(id, FnrhStatus.CONFIRMED);
        auditService.record(AuditAction.UPDATE, AuditService.FNRH, id);
        return registration;
    }

    @Transactional(readOnly = true)
    public List<FnrhRegistration> list() {
        return fnrhRegistrationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public FnrhRegistration get(UUID id) {
        return fnrhRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FNRH não encontrada."));
    }

    @Transactional
    public FnrhRegistration updateStatus(UUID id, FnrhStatus status) {
        FnrhRegistration registration = get(id);
        registration.changeStatus(status);
        fnrhRegistrationRepository.flush();
        return registration;
    }

    @Transactional
    public void delete(UUID id) {
        FnrhRegistration registration = get(id);
        fnrhRegistrationRepository.delete(registration);
        fnrhRegistrationRepository.flush();
    }
}
