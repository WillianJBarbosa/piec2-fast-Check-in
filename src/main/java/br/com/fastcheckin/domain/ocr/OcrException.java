package br.com.fastcheckin.domain.ocr;

import java.util.List;

/**
 * Falha ao ler o documento, com o status HTTP que a API deve devolver e um
 * código estável. As mensagens nunca trazem dados pessoais.
 */
public class OcrException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<String> details;

    public OcrException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public OcrException(int status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
