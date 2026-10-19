package br.com.fastcheckin.domain.stay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StayRepository extends JpaRepository<Stay, UUID> {
}
