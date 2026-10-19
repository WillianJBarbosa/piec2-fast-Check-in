package br.com.fastcheckin.domain.guest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GuestService {

    private final GuestRepository guestRepository;
    private final PersonalDataCipher personalDataCipher;
    private final ObjectMapper objectMapper;

    public GuestService(
            GuestRepository guestRepository,
            PersonalDataCipher personalDataCipher,
            ObjectMapper objectMapper
    ) {
        this.guestRepository = guestRepository;
        this.personalDataCipher = personalDataCipher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Guest register(GuestPersonalData personalData) {
        try {
            String json = objectMapper.writeValueAsString(personalData);
            return guestRepository.save(new Guest(personalDataCipher.encrypt(json)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar os dados do hóspede.", exception);
        }
    }

    @Transactional(readOnly = true)
    public GuestPersonalData getPersonalData(UUID guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Hóspede não encontrado."));
        try {
            String json = personalDataCipher.decrypt(guest.getPersonalDataCiphertext());
            return objectMapper.readValue(json, GuestPersonalData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível ler os dados do hóspede armazenados.", exception);
        }
    }
}
