package br.com.fastcheckin.domain.guest;

import br.com.fastcheckin.common.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        return guestRepository.save(new Guest(encrypt(personalData)));
    }

    @Transactional(readOnly = true)
    public GuestPersonalData getPersonalData(UUID guestId) {
        return decrypt(findGuest(guestId));
    }

    @Transactional(readOnly = true)
    public GuestView get(UUID guestId) {
        return toView(findGuest(guestId));
    }

    @Transactional(readOnly = true)
    public List<GuestView> list() {
        return guestRepository.findAll().stream().map(this::toView).toList();
    }

    @Transactional
    public GuestView update(UUID guestId, GuestPersonalData personalData) {
        Guest guest = findGuest(guestId);
        guest.replacePersonalDataCiphertext(encrypt(personalData));
        guestRepository.flush();
        return toView(guest);
    }

    @Transactional
    public void delete(UUID guestId) {
        Guest guest = findGuest(guestId);
        guestRepository.delete(guest);
        guestRepository.flush();
    }

    private Guest findGuest(UUID guestId) {
        return guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóspede não encontrado."));
    }

    private GuestView toView(Guest guest) {
        return new GuestView(guest.getId(), decrypt(guest), guest.getCreatedAt());
    }

    private String encrypt(GuestPersonalData personalData) {
        try {
            return personalDataCipher.encrypt(objectMapper.writeValueAsString(personalData));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar os dados do hóspede.", exception);
        }
    }

    private GuestPersonalData decrypt(Guest guest) {
        try {
            String json = personalDataCipher.decrypt(guest.getPersonalDataCiphertext());
            return objectMapper.readValue(json, GuestPersonalData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível ler os dados do hóspede armazenados.", exception);
        }
    }
}
