package br.com.fastcheckin.domain;

import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.guest.GuestService;
import br.com.fastcheckin.domain.fnrh.FnrhRegistration;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
import br.com.fastcheckin.domain.fnrh.FnrhStatus;
import br.com.fastcheckin.domain.hotel.Hotel;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.hotel.HotelService;
import br.com.fastcheckin.domain.stay.Stay;
import br.com.fastcheckin.domain.stay.StayRepository;
import br.com.fastcheckin.domain.stay.StayService;
import br.com.fastcheckin.domain.stay.StayStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainPersistenceTest {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private GuestService guestService;

    @Autowired
    private StayService stayService;

    @Autowired
    private FnrhRegistrationService fnrhRegistrationService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Test
    void persistsHotelGuestAndStayAndKeepsGuestDataEncrypted() {
        Hotel hotel = hotelService.register("Hotel de Teste");
        GuestPersonalData personalData = new GuestPersonalData(
                "Pessoa de Teste",
                "12345678901",
                LocalDate.of(1990, 4, 12),
                DocumentType.CNH,
                "TESTE-123"
        );
        Guest guest = guestService.register(personalData);
        Stay stay = stayService.schedule(hotel.getId(), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5));
        FnrhRegistration fnrh = fnrhRegistrationService.register(stay.getId(), guest.getId());

        assertThat(hotelRepository.findById(hotel.getId())).isPresent();
        assertThat(guestRepository.findById(guest.getId())).isPresent();
        assertThat(stayRepository.findById(stay.getId()))
                .get()
                .satisfies(persistedStay -> {
                    assertThat(persistedStay.getHotel().getId()).isEqualTo(hotel.getId());
                    assertThat(persistedStay.getStatus()).isEqualTo(StayStatus.PENDING);
                });
        assertThat(fnrhRegistrationRepository.findById(fnrh.getId()))
                .get()
                .satisfies(persistedFnrh -> {
                    assertThat(persistedFnrh.getStay().getId()).isEqualTo(stay.getId());
                    assertThat(persistedFnrh.getGuest().getId()).isEqualTo(guest.getId());
                    assertThat(persistedFnrh.getStatus()).isEqualTo(FnrhStatus.PENDING);
                });
        assertThat(guest.getPersonalDataCiphertext())
                .startsWith("v1:")
                .doesNotContain(personalData.cpf())
                .doesNotContain(personalData.fullName());
        assertThat(guestService.getPersonalData(guest.getId())).isEqualTo(personalData);
    }
}
