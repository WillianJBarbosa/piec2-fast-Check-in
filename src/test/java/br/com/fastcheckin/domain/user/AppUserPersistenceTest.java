package br.com.fastcheckin.domain.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AppUserPersistenceTest {

    @Autowired
    private AppUserRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void savesUserWithRoleAndActiveFlag() {
        AppUser saved = repository.saveAndFlush(new AppUser("maria", "hash-de-teste", UserRole.RECEPCIONISTA));

        AppUser found = repository.findByUsername("maria").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getRole()).isEqualTo(UserRole.RECEPCIONISTA);
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateUsername() {
        repository.saveAndFlush(new AppUser("joao", "hash-1", UserRole.ADMIN));

        assertThatThrownBy(() -> repository.saveAndFlush(new AppUser("joao", "hash-2", UserRole.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
