package br.com.fastcheckin.domain.facial;

/**
 * Falha na verificação facial, com o status HTTP que a API deve devolver e um
 * código estável. As mensagens nunca trazem dados pessoais.
 */
public class FacialException extends RuntimeException {

    private final int status;
    private final String code;

    public FacialException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
