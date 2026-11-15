package br.com.fastcheckin.api;

import br.com.fastcheckin.security.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(
            @NotBlank(message = "o usuário é obrigatório") String username,
            @NotBlank(message = "a senha é obrigatória") String password
    ) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, String role) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Login login = authService.login(request.username(), request.password());
        return new LoginResponse(
                login.token().accessToken(),
                "Bearer",
                login.token().expiresInSeconds(),
                login.user().getRole().name()
        );
    }
}
