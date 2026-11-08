package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.fnrh.FnrhRegistration;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
import br.com.fastcheckin.domain.fnrh.FnrhStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fnrh-registrations")
public class FnrhRegistrationController {

    private final FnrhRegistrationService fnrhRegistrationService;

    public FnrhRegistrationController(FnrhRegistrationService fnrhRegistrationService) {
        this.fnrhRegistrationService = fnrhRegistrationService;
    }

    public record FnrhRequest(
            @NotNull(message = "a estadia é obrigatória") UUID stayId,
            @NotNull(message = "o hóspede é obrigatório") UUID guestId
    ) {
    }

    public record FnrhStatusRequest(
            @NotNull(message = "o status é obrigatório (PENDING ou CONFIRMED)") FnrhStatus status
    ) {
    }

    public record FnrhResponse(UUID id, UUID stayId, UUID guestId, FnrhStatus status, Instant createdAt) {

        static FnrhResponse from(FnrhRegistration registration) {
            return new FnrhResponse(
                    registration.getId(),
                    registration.getStay().getId(),
                    registration.getGuest().getId(),
                    registration.getStatus(),
                    registration.getCreatedAt()
            );
        }
    }

    @PostMapping
    public ResponseEntity<FnrhResponse> create(@Valid @RequestBody FnrhRequest request) {
        FnrhRegistration registration = fnrhRegistrationService.register(request.stayId(), request.guestId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(registration.getId())
                .toUri();
        return ResponseEntity.created(location).body(FnrhResponse.from(registration));
    }

    @GetMapping
    public List<FnrhResponse> list() {
        return fnrhRegistrationService.list().stream().map(FnrhResponse::from).toList();
    }

    @GetMapping("/{id}")
    public FnrhResponse get(@PathVariable UUID id) {
        return FnrhResponse.from(fnrhRegistrationService.get(id));
    }

    @PutMapping("/{id}")
    public FnrhResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody FnrhStatusRequest request) {
        return FnrhResponse.from(fnrhRegistrationService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        fnrhRegistrationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
