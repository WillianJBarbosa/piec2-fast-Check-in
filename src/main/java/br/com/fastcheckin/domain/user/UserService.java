package br.com.fastcheckin.domain.user;

import br.com.fastcheckin.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser create(String username, String rawPassword, UserRole role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("O usuário é obrigatório.");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        }
        if (role == null) {
            throw new IllegalArgumentException("O perfil é obrigatório.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return userRepository.saveAndFlush(new AppUser(normalized, passwordEncoder.encode(rawPassword), role));
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return userRepository.findAll();
    }

    @Transactional
    public void deactivate(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        user.deactivate();
        userRepository.flush();
    }

    @Transactional(readOnly = true)
    public boolean hasAnyUser() {
        return userRepository.count() > 0;
    }
}
