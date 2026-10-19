package br.com.fastcheckin.domain.guest;

import java.time.LocalDate;

public record GuestPersonalData(
        String fullName,
        String cpf,
        LocalDate dateOfBirth,
        DocumentType documentType,
        String documentNumber
) {
    public GuestPersonalData {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("O nome do hóspede é obrigatório.");
        }
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("O CPF do hóspede é obrigatório.");
        }
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("A data de nascimento do hóspede é obrigatória.");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("O tipo do documento é obrigatório.");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException("O número do documento é obrigatório.");
        }
    }
}
