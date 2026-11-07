package br.com.fastcheckin.domain.guest;

import java.time.Instant;
import java.util.UUID;

/**
 * Hóspede com os dados pessoais já decifrados. Só deve existir em memória,
 * durante o atendimento da requisição.
 */
public record GuestView(UUID id, GuestPersonalData personalData, Instant createdAt) {
}
