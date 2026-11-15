package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.consent.Consent;
import br.com.fastcheckin.domain.consent.ConsentPurpose;
import br.com.fastcheckin.domain.consent.ConsentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consentimentos de um hóspede, por finalidade. O de CHECKIN_FNRH é criado junto com o hóspede. */
@RestController
@RequestMapping("/api/v1/guests/{guestId}/consents")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    public record ConsentRequest(
            @NotNull(message = "a finalidade é obrigatória (CHECKIN_FNRH ou BIOMETRIA_FACIAL)")
            ConsentPurpose purpose
    ) {
    }

    public record ConsentResponse(
            UUID id,
            ConsentPurpose purpose,
            String description,
            Instant grantedAt,
            Instant revokedAt,
            String recordedBy,
            boolean active
    ) {

        static ConsentResponse from(Consent consent) {
            return new ConsentResponse(
                    consent.getId(),
                    consent.getPurpose(),
                    consent.getPurpose().getDescription(),
                    consent.getGrantedAt(),
                    consent.getRevokedAt(),
                    consent.getRecordedBy(),
                    consent.isActive()
            );
        }
    }

    @PostMapping
    public ResponseEntity<ConsentResponse> grant(
            @PathVariable UUID guestId,
            @Valid @RequestBody ConsentRequest request
    ) {
        Consent consent = consentService.grant(guestId, request.purpose());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsentResponse.from(consent));
    }

    @GetMapping
    public List<ConsentResponse> list(@PathVariable UUID guestId) {
        return consentService.list(guestId).stream().map(ConsentResponse::from).toList();
    }

    @DeleteMapping("/{purpose}")
    public ResponseEntity<Void> revoke(@PathVariable UUID guestId, @PathVariable ConsentPurpose purpose) {
        consentService.revoke(guestId, purpose);
        return ResponseEntity.noContent().build();
    }
}
