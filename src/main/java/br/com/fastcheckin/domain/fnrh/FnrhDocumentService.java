package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.consent.ConsentRequiredException;
import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.ocr.OcrClient;
import br.com.fastcheckin.domain.ocr.OcrException;
import br.com.fastcheckin.domain.ocr.OcrReading;
import br.com.fastcheckin.domain.stay.StayRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Check-in a partir da foto do documento: lê com o serviço de OCR e cria o
 * hóspede e a FNRH pendente de revisão. Não é transacional de propósito: a
 * chamada ao OCR pode demorar e não deve segurar uma transação do banco. A
 * gravação acontece em {@link FnrhRegistrationService#registerFromDocument}.
 * A imagem existe só na memória desta chamada: nada é gravado nem registrado.
 */
@Service
public class FnrhDocumentService {

    public record Result(FnrhRegistration registration, GuestPersonalData personalData, List<String> reviewReasons) {
    }

    private final StayRepository stayRepository;
    private final OcrClient ocrClient;
    private final FnrhRegistrationService fnrhRegistrationService;

    public FnrhDocumentService(
            StayRepository stayRepository,
            OcrClient ocrClient,
            FnrhRegistrationService fnrhRegistrationService
    ) {
        this.stayRepository = stayRepository;
        this.ocrClient = ocrClient;
        this.fnrhRegistrationService = fnrhRegistrationService;
    }

    public Result registerFromDocument(UUID stayId, boolean consentAccepted, byte[] image, String contentType) {
        // O aceite vem antes de qualquer processamento do documento.
        if (!consentAccepted) {
            throw new ConsentRequiredException(
                    "O consentimento do hóspede é obrigatório antes de processar o documento (consentAccepted = true)."
            );
        }
        if (!stayRepository.existsById(stayId)) {
            throw new ResourceNotFoundException("Estadia não encontrada.");
        }

        OcrReading reading = ocrClient.read(image, contentType);
        GuestPersonalData personalData = toPersonalData(reading);

        FnrhRegistration registration = fnrhRegistrationService.registerFromDocument(
                stayId, personalData, reading.needsReview());
        List<String> reasons = reading.reasons() == null ? List.of() : List.copyOf(reading.reasons());
        return new Result(registration, personalData, reasons);
    }

    /**
     * O hóspede só é criado com os cinco campos válidos. Se o OCR não leu
     * algum, nada é gravado e a resposta diz quais faltaram.
     */
    static GuestPersonalData toPersonalData(OcrReading reading) {
        List<String> missing = new ArrayList<>();

        String fullName = clean(reading.fullName());
        if (fullName == null || fullName.length() > 200) {
            missing.add("nome");
        }
        String cpf = clean(reading.cpf());
        if (cpf == null || !cpf.matches("\\d{11}")) {
            missing.add("CPF");
        }
        LocalDate dateOfBirth = parseDate(reading.dateOfBirth());
        if (dateOfBirth == null || !dateOfBirth.isBefore(LocalDate.now())) {
            missing.add("data de nascimento");
        }
        DocumentType documentType = parseType(reading.documentType());
        if (documentType == null) {
            missing.add("tipo do documento");
        }
        String documentNumber = clean(reading.documentNumber());
        if (documentNumber == null || documentNumber.length() > 30) {
            missing.add("número do documento");
        }

        if (!missing.isEmpty()) {
            throw new OcrException(
                    422,
                    "DOCUMENTO_INCOMPLETO",
                    "Não consegui ler todos os dados do documento (faltou: " + String.join(", ", missing)
                            + "). Fotografe de novo ou cadastre o hóspede manualmente.",
                    missing.stream().map(field -> "campo não lido: " + field).toList()
            );
        }
        return new GuestPersonalData(fullName, cpf, dateOfBirth, documentType, documentNumber);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDate parseDate(String value) {
        String text = clean(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static DocumentType parseType(String value) {
        String text = clean(value);
        if (text == null) {
            return null;
        }
        try {
            return DocumentType.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
