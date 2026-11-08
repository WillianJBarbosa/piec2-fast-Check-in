package br.com.fastcheckin.domain.stay;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StayRepository extends JpaRepository<Stay, UUID> {

    // O hotel é carregado junto para a API poder ler o id fora da transação.
    @Override
    @EntityGraph(attributePaths = "hotel")
    List<Stay> findAll();

    @Override
    @EntityGraph(attributePaths = "hotel")
    Optional<Stay> findById(UUID id);

    @EntityGraph(attributePaths = "hotel")
    List<Stay> findByHotelId(UUID hotelId);
}
