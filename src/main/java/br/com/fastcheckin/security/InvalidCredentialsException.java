package br.com.fastcheckin.security;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Usuário ou senha inválidos.");
    }
}
