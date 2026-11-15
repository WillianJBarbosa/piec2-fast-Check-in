package br.com.fastcheckin.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Nome do usuário autenticado na requisição atual; "SISTEMA" fora de uma requisição (rotinas agendadas). */
public final class CurrentUser {

    public static final String SYSTEM = "SISTEMA";

    private CurrentUser() {
    }

    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return SYSTEM;
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
