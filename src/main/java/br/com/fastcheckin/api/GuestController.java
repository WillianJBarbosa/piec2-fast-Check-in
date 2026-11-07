package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.guest.GuestService;
import br.com.fastcheckin.domain.guest.GuestView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rotas administrativas de hóspedes. As respostas nunca trazem o texto cifrado
 * nem CPF e documento completos: ficam mascarados. A autenticação desta rota
 * entra na meta de segurança (M10).
 */
@RestController
@RequestMapping("/api/v1/guests")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    public record GuestRequest(
            @NotBlank(message = "o nome do hóspede é obrigatório")
            @Size(max = 200, message = "o nome deve ter no máximo 200 caracteres")
            String fullName,
            @NotBlank(message = "o CPF é obrigatório")
            @Pattern(regexp = "\\d{11}", message = "o CPF deve ter 11 dígitos, sem pontos ou traço")
            String cpf,
            @NotNull(message = "a data de nascimento é obrigatória")
            @Past(message = "a data de nascimento deve estar no passado")
            LocalDate dateOfBirth,
            @NotNull(message = "o tipo do documento é obrigatório (RG ou CNH)")
            DocumentType documentType,
            @NotBlank(message = "o número do documento é obrigatório")
            @Size(max = 30, message = "o número do documento deve ter no máximo 30 caracteres")
            String documentNumber
    ) {

        GuestPersonalData toPersonalData() {
            return new GuestPersonalData(fullName, cpf, dateOfBirth, documentType, documentNumber);
        }
    }

    public record GuestResponse(
            UUID id,
            String fullName,
            String cpfMasked,
            LocalDate dateOfBirth,
            DocumentType documentType,
            String documentNumberMasked,
            Instant createdAt
    ) {

        static GuestResponse from(GuestView view) {
            GuestPersonalData data = view.personalData();
            return new GuestResponse(
                    view.id(),
                    data.fullName(),
                    maskCpf(data.cpf()),
                    data.dateOfBirth(),
                    data.documentType(),
                    maskKeepingLast(data.documentNumber(), 3),
                    view.createdAt()
            );
        }

        static String maskCpf(String cpf) {
            if (cpf == null || cpf.length() != 11) {
                return maskKeepingLast(cpf, 2);
            }
            return "***.***.***-" + cpf.substring(9);
        }

        static String maskKeepingLast(String value, int visible) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            int hidden = Math.max(0, value.length() - visible);
            return "*".repeat(hidden) + value.substring(hidden);
        }
    }

    @PostMapping
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody GuestRequest request) {
        UUID id = guestService.register(request.toPersonalData()).getId();
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(GuestResponse.from(guestService.get(id)));
    }

    @GetMapping
    public List<GuestResponse> list() {
        return guestService.list().stream().map(GuestResponse::from).toList();
    }

    @GetMapping("/{id}")
    public GuestResponse get(@PathVariable UUID id) {
        return GuestResponse.from(guestService.get(id));
    }

    @PutMapping("/{id}")
    public GuestResponse update(@PathVariable UUID id, @Valid @RequestBody GuestRequest request) {
        return GuestResponse.from(guestService.update(id, request.toPersonalData()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        guestService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
