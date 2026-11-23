package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.facial.FacialException;
import br.com.fastcheckin.domain.facial.FacialResult;
import br.com.fastcheckin.domain.facial.FacialVerificationService;
import br.com.fastcheckin.domain.fnrh.FnrhDocumentService;
import br.com.fastcheckin.domain.fnrh.FnrhRegistration;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
import br.com.fastcheckin.domain.fnrh.FnrhStatus;
import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.ocr.OcrException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fnrh-registrations")
public class FnrhRegistrationController {

    private final FnrhRegistrationService fnrhRegistrationService;
    private final FnrhDocumentService fnrhDocumentService;
    private final FacialVerificationService facialVerificationService;
    private final int maxImageBytes;
    private final int maxFacialImageBytes;

    public FnrhRegistrationController(
            FnrhRegistrationService fnrhRegistrationService,
            FnrhDocumentService fnrhDocumentService,
            FacialVerificationService facialVerificationService,
            @Value("${app.ocr.max-image-bytes:8388608}") int maxImageBytes,
            @Value("${app.facial.max-image-bytes:8388608}") int maxFacialImageBytes
    ) {
        this.fnrhRegistrationService = fnrhRegistrationService;
        this.fnrhDocumentService = fnrhDocumentService;
        this.facialVerificationService = facialVerificationService;
        this.maxImageBytes = maxImageBytes;
        this.maxFacialImageBytes = maxFacialImageBytes;
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

    public record FnrhResponse(
            UUID id,
            UUID stayId,
            UUID guestId,
            FnrhStatus status,
            boolean reviewRequired,
            FacialResult facialResult,
            boolean facialConfirmedManually,
            Instant createdAt
    ) {

        static FnrhResponse from(FnrhRegistration registration) {
            return new FnrhResponse(
                    registration.getId(),
                    registration.getStay().getId(),
                    registration.getGuest().getId(),
                    registration.getStatus(),
                    registration.isReviewRequired(),
                    registration.getFacialResult(),
                    registration.isFacialConfirmedManually(),
                    registration.getCreatedAt()
            );
        }
    }

    /** Campos lidos do documento, completos, para o recepcionista conferir com o documento. */
    public record ExtractedData(
            String fullName,
            String cpf,
            LocalDate dateOfBirth,
            DocumentType documentType,
            String documentNumber
    ) {

        static ExtractedData from(GuestPersonalData data) {
            return new ExtractedData(
                    data.fullName(), data.cpf(), data.dateOfBirth(), data.documentType(), data.documentNumber());
        }
    }

    public record FnrhDocumentResponse(
            FnrhResponse fnrh,
            ExtractedData extracted,
            boolean reviewRequired,
            List<String> reviewReasons
    ) {
    }

    /** As duas imagens em Base64 (JPEG, PNG, WEBP ou BMP). Não é multipart, para nada ir para o disco. */
    public record FacialCheckRequest(
            @NotBlank(message = "a foto do documento é obrigatória") String documentImage,
            @NotBlank(message = "a selfie é obrigatória") String selfieImage
    ) {
    }

    public record FacialCheckResponse(
            UUID fnrhId,
            FacialResult result,
            Double ratio,
            String reason,
            boolean needsReview,
            boolean confirmedManually
    ) {
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

    /**
     * Check-in pela foto do documento. O corpo são os bytes da imagem
     * (Content-Type image/*), sem multipart, para nada ir para disco. Exige
     * consentAccepted=true. Cria o hóspede e a FNRH pendente; o recepcionista
     * confere e confirma em POST /{id}/confirm. A resposta traz os dados
     * completos e não deve ser guardada em cache.
     */
    @PostMapping("/from-document")
    public ResponseEntity<FnrhDocumentResponse> createFromDocument(
            @RequestParam UUID stayId,
            @RequestParam(defaultValue = "false") boolean consentAccepted,
            HttpServletRequest request
    ) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new OcrException(415, "FORMATO_NAO_SUPORTADO",
                    "Envie a imagem do documento no corpo da requisição, com Content-Type image/jpeg, image/png, image/webp ou image/bmp.");
        }
        byte[] image = request.getInputStream().readNBytes(maxImageBytes + 1);
        if (image.length > maxImageBytes) {
            throw new OcrException(413, "IMAGEM_GRANDE_DEMAIS",
                    "A imagem é maior que o limite aceito. Reduza a resolução da foto.");
        }
        if (image.length == 0) {
            throw new OcrException(422, "IMAGEM_VAZIA", "A requisição não trouxe nenhuma imagem.");
        }

        FnrhDocumentService.Result result =
                fnrhDocumentService.registerFromDocument(stayId, consentAccepted, image, contentType);
        FnrhRegistration registration = result.registration();
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/fnrh-registrations/{id}")
                .buildAndExpand(registration.getId())
                .toUri();
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(new FnrhDocumentResponse(
                        FnrhResponse.from(registration),
                        ExtractedData.from(result.personalData()),
                        registration.isReviewRequired(),
                        result.reviewReasons()
                ));
    }

    /**
     * Compara o rosto da foto do documento com o da selfie (serviço facial).
     * Exige o consentimento BIOMETRIA_FACIAL do hóspede. Devolve compativel,
     * revisar ou diferente: o sistema nunca barra o hóspede sozinho, e o
     * recepcionista segue com POST /{id}/face-check/confirm depois de conferir.
     * Só o resultado é gravado; as imagens ficam na memória e a resposta não
     * deve ser guardada em cache.
     */
    @PostMapping("/{id}/face-check")
    public ResponseEntity<FacialCheckResponse> faceCheck(
            @PathVariable UUID id,
            @Valid @RequestBody FacialCheckRequest request
    ) {
        byte[] document = decodeImage("documentImage", request.documentImage());
        byte[] selfie = decodeImage("selfieImage", request.selfieImage());

        FacialVerificationService.Result result = facialVerificationService.verify(id, document, selfie);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new FacialCheckResponse(
                        result.registration().getId(),
                        result.comparison().result(),
                        result.comparison().ratio(),
                        result.comparison().reason(),
                        result.comparison().needsReview(),
                        result.registration().isFacialConfirmedManually()
                ));
    }

    /** O recepcionista conferiu o hóspede pessoalmente e segue (exige uma comparação feita antes). */
    @PostMapping("/{id}/face-check/confirm")
    public FnrhResponse confirmFaceCheck(@PathVariable UUID id) {
        return FnrhResponse.from(fnrhRegistrationService.confirmFacialManually(id));
    }

    private byte[] decodeImage(String field, String base64) {
        // Recusa antes de decodificar: o Base64 tem cerca de 4/3 do tamanho original.
        if (base64.length() > (maxFacialImageBytes * 4L) / 3 + 8) {
            throw new FacialException(413, "IMAGEM_GRANDE_DEMAIS",
                    "A imagem '" + field + "' é maior que o limite aceito. Reduza a resolução da foto.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new FacialException(422, "IMAGEM_INVALIDA", "A imagem '" + field + "' não está em Base64 válido.");
        }
        if (bytes.length == 0) {
            throw new FacialException(422, "IMAGEM_VAZIA", "A imagem '" + field + "' está vazia.");
        }
        if (bytes.length > maxFacialImageBytes) {
            throw new FacialException(413, "IMAGEM_GRANDE_DEMAIS",
                    "A imagem '" + field + "' é maior que o limite aceito. Reduza a resolução da foto.");
        }
        return bytes;
    }

    /** Confirmação do recepcionista, depois de conferir os dados com o documento. */
    @PostMapping("/{id}/confirm")
    public FnrhResponse confirm(@PathVariable UUID id) {
        return FnrhResponse.from(fnrhRegistrationService.confirm(id));
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
