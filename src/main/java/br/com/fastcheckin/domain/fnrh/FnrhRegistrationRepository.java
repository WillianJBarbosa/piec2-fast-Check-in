package br.com.fastcheckin.domain.fnrh;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FnrhRegistrationRepository extends JpaRepository<FnrhRegistration, UUID> {

    // Estadia e hóspede são carregados junto para a API poder ler os ids fora da transação.
    @Override
    @EntityGraph(attributePaths = {"stay", "guest"})
    List<FnrhRegistration> findAll();

    @Override
    @EntityGraph(attributePaths = {"stay", "guest"})
    Optional<FnrhRegistration> findById(UUID id);
}
