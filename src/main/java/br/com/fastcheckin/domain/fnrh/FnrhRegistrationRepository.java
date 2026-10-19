package br.com.fastcheckin.domain.fnrh;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FnrhRegistrationRepository extends JpaRepository<FnrhRegistration, UUID> {
}
