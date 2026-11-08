package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.stay.Stay;
import br.com.fastcheckin.domain.stay.StayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FnrhRegistrationService {

    private final FnrhRegistrationRepository fnrhRegistrationRepository;
    private final StayRepository stayRepository;
    private final GuestRepository guestRepository;

    public FnrhRegistrationService(
            FnrhRegistrationRepository fnrhRegistrationRepository,
            StayRepository stayRepository,
            GuestRepository guestRepository
    ) {
        this.fnrhRegistrationRepository = fnrhRegistrationRepository;
        this.stayRepository = stayRepository;
        this.guestRepository = guestRepository;
    }

    @Transactional
    public FnrhRegistration register(UUID stayId, UUID guestId) {
        Stay stay = stayRepository.findById(stayId)
                .orElseThrow(() -> new ResourceNotFoundException("Estadia não encontrada."));
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Hóspede não encontrado."));
        return fnrhRegistrationRepository.saveAndFlush(new FnrhRegistration(stay, guest));
    }

    @Transactional(readOnly = true)
    public List<FnrhRegistration> list() {
        return fnrhRegistrationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public FnrhRegistration get(UUID id) {
        return fnrhRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FNRH não encontrada."));
    }

    @Transactional
    public FnrhRegistration updateStatus(UUID id, FnrhStatus status) {
        FnrhRegistration registration = get(id);
        registration.changeStatus(status);
        fnrhRegistrationRepository.flush();
        return registration;
    }

    @Transactional
    public void delete(UUID id) {
        FnrhRegistration registration = get(id);
        fnrhRegistrationRepository.delete(registration);
        fnrhRegistrationRepository.flush();
    }
}
