package br.com.fastcheckin.security;

import br.com.fastcheckin.domain.user.AppUser;
import br.com.fastcheckin.domain.user.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    // Hash usado quando o usuário não existe, para o tempo de resposta não revelar quais usuários existem.
    private final String dummyHash;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("senha-que-nunca-sera-usada");
    }

    public record Login(TokenService.IssuedToken token, AppUser user) {
    }

    @Transactional(readOnly = true)
    public Login login(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> found = userRepository.findByUsername(normalized);
        String hash = found.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
            throw new InvalidCredentialsException();
        }
        return new Login(tokenService.issue(found.get()), found.get());
    }
}
