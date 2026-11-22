package br.com.fastcheckin.domain.ocr;

import java.util.List;

/**
 * Resultado da leitura de um documento pelo serviço de OCR. Os campos vêm como
 * texto e podem ser nulos quando o OCR não os encontrou; quem usa valida antes
 * de cadastrar. Contém dados pessoais: não registrar em log.
 */
public record OcrReading(
        String fullName,
        String cpf,
        String dateOfBirth,
        String documentType,
        String documentNumber,
        boolean needsReview,
        List<String> reasons,
        String engine
) {
}
