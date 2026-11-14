package br.com.fastcheckin.security;

import br.com.fastcheckin.domain.user.UserRole;
import br.com.fastcheckin.domain.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro ADMIN na inicialização quando ainda não existe nenhum usuário e as
 * variáveis ADMIN_USERNAME e ADMIN_PASSWORD estão definidas. A senha nunca vai para o log.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserService userService;
    private final String username;
    private final String password;

    public BootstrapAdmin(
            UserService userService,
            @Value("${app.security.bootstrap-admin-username:}") String username,
            @Value("${app.security.bootstrap-admin-password:}") String password
    ) {
        this.userService = userService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            if (!userService.hasAnyUser()) {
                LOG.warn("Nenhum usuário cadastrado. Defina ADMIN_USERNAME e ADMIN_PASSWORD para criar o primeiro ADMIN.");
            }
            return;
        }
        if (userService.hasAnyUser()) {
            return;
        }
        userService.create(username, password, UserRole.ADMIN);
        LOG.info("Usuário ADMIN inicial criado: {}", username.trim().toLowerCase());
    }
}
