package br.com.fastcheckin.domain.consent;

public class ConsentRequiredException extends RuntimeException {

    public ConsentRequiredException(String message) {
        super(message);
    }
}
