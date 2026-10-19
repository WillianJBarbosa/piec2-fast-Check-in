package br.com.fastcheckin.domain.fnrh;

import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.stay.Stay;
import br.com.fastcheckin.domain.stay.StayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new IllegalArgumentException("Estadia não encontrada."));
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Hóspede não encontrado."));
        return fnrhRegistrationRepository.save(new FnrhRegistration(stay, guest));
    }
}
