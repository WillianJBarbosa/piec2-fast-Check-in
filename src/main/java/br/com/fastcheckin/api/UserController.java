package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.user.AppUser;
import br.com.fastcheckin.domain.user.UserRole;
import br.com.fastcheckin.domain.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestão de usuários, restrita ao perfil ADMIN (ver SecurityConfig).
 * As respostas nunca trazem a senha nem o hash.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserRequest(
            @NotBlank(message = "o usuário é obrigatório")
            @Size(min = 3, max = 60, message = "o usuário deve ter entre 3 e 60 caracteres")
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "o usuário aceita só letras, números, ponto, hífen e sublinhado")
            String username,
            @NotBlank(message = "a senha é obrigatória")
            @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres")
            String password,
            @NotNull(message = "o perfil é obrigatório")
            UserRole role
    ) {
    }

    public record UserResponse(UUID id, String username, UserRole role, boolean active, Instant createdAt) {

        static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isActive(), user.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        AppUser user = userService.create(request.username(), request.password(), request.role());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();
        return ResponseEntity.created(location).body(UserResponse.from(user));
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    /** Desativa o usuário (ele deixa de poder entrar), sem apagar o registro. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
