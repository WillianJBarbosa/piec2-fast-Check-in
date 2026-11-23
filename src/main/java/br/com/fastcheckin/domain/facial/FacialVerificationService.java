package br.com.fastcheckin.domain.facial;

import br.com.fastcheckin.domain.consent.ConsentPurpose;
import br.com.fastcheckin.domain.consent.ConsentService;
import br.com.fastcheckin.domain.fnrh.FnrhRegistration;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Comparação facial entre a foto do documento e a selfie do hóspede de uma FNRH.
 * Não é transacional de propósito: a chamada ao serviço facial pode demorar e
 * não deve segurar uma transação do banco. O resultado é gravado em
 * {@link FnrhRegistrationService#recordFacialResult}. As imagens existem só na
 * memória desta chamada: nada é gravado nem registrado.
 */
@Service
public class FacialVerificationService {

    public record Result(FnrhRegistration registration, FacialComparison comparison) {
    }

    private final FnrhRegistrationService fnrhRegistrationService;
    private final ConsentService consentService;
    private final FacialClient facialClient;

    public FacialVerificationService(
            FnrhRegistrationService fnrhRegistrationService,
            ConsentService consentService,
            FacialClient facialClient
    ) {
        this.fnrhRegistrationService = fnrhRegistrationService;
        this.consentService = consentService;
        this.facialClient = facialClient;
    }

    public Result verify(UUID fnrhId, byte[] documentImage, byte[] selfieImage) {
        FnrhRegistration registration = fnrhRegistrationService.get(fnrhId); // 404 se não existir
        // O consentimento de biometria facial vem antes de qualquer processamento do rosto.
        consentService.requireActive(registration.getGuest().getId(), ConsentPurpose.BIOMETRIA_FACIAL);

        FacialComparison comparison = facialClient.compare(documentImage, selfieImage);
        FnrhRegistration updated = fnrhRegistrationService.recordFacialResult(fnrhId, comparison.result());
        return new Result(updated, comparison);
    }
}
