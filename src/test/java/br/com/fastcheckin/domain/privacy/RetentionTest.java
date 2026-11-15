package br.com.fastcheckin.domain.privacy;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditEntry;
import br.com.fastcheckin.domain.audit.AuditEntryRepository;
import br.com.fastcheckin.domain.consent.ConsentRepository;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.Guest;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.guest.GuestService;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.hotel.HotelService;
import br.com.fastcheckin.domain.stay.StayRepository;
import br.com.fastcheckin.domain.stay.StayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa apenas dados sintéticos. O tempo é simulado passando "now" à rotina de retenção. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin-teste", roles = "ADMIN")
class RetentionTest {

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private GuestService guestService;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private StayService stayService;

    @Autowired
    private FnrhRegistrationService fnrhRegistrationService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void cleanUp() {
        fnrhRegistrationRepository.deleteAll();
        stayRepository.deleteAll();
        guestRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private Guest newGuest() {
        return guestService.register(new GuestPersonalData(
                "Pessoa de Teste",
                "12345678901",
                LocalDate.of(1990, 4, 12),
                DocumentType.CNH,
                "TESTE-123"
        ));
    }

    private Instant afterRetention(long extraDays) {
        return Instant.now().plus(retentionService.retentionDays() + extraDays, ChronoUnit.DAYS);
    }

    @Test
    void anonymizesGuestsPastTheRetentionPeriod() throws Exception {
        UUID id = newGuest().getId();

        int count = retentionService.anonymizeExpired(afterRetention(1));

        assertThat(count).isGreaterThanOrEqualTo(1);
        Guest stored = guestRepository.findById(id).orElseThrow();
        assertThat(stored.isAnonymized()).isTrue();
        assertThat(stored.getPersonalDataCiphertext()).isNull();
        assertThat(consentRepository.findByGuestIdAndRevokedAtIsNull(id)).isEmpty();
        assertThat(auditEntryRepository.findByResourceIdOrderByOccurredAtDesc(id, PageRequest.of(0, 50)))
                .extracting(AuditEntry::getAction)
                .contains(AuditAction.ANONYMIZE);
        assertThatThrownBy(() -> guestService.get(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(guestService.list()).noneMatch(view -> view.id().equals(id));
    }

    @Test
    void anonymizedGuestCannotReceiveNewConsentOrFnrh() throws Exception {
        UUID id = newGuest().getId();
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        UUID stayId = stayService.schedule(hotelId, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 5)).getId();
        retentionService.anonymizeExpired(afterRetention(1000));

        mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHECKIN_FNRH\"}"))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> fnrhRegistrationService.register(stayId, id))
                .isInstanceOf(br.com.fastcheckin.domain.consent.ConsentRequiredException.class);
    }

    @Test
    void keepsGuestsWithinTheRetentionPeriod() {
        UUID id = newGuest().getId();

        retentionService.anonymizeExpired(Instant.now());

        assertThat(guestRepository.findById(id).orElseThrow().isAnonymized()).isFalse();
        assertThat(guestService.getPersonalData(id).fullName()).isEqualTo("Pessoa de Teste");
    }

    @Test
    void keepsGuestWithARecentStayUntilItAlsoExpires() {
        UUID id = newGuest().getId();
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        LocalDate arrival = LocalDate.now().plusDays(390);
        UUID stayId = stayService.schedule(hotelId, arrival, arrival.plusDays(10)).getId();
        fnrhRegistrationService.register(stayId, id);

        retentionService.anonymizeExpired(afterRetention(1));
        assertThat(guestRepository.findById(id).orElseThrow().isAnonymized()).isFalse();

        retentionService.anonymizeExpired(afterRetention(800));
        assertThat(guestRepository.findById(id).orElseThrow().isAnonymized()).isTrue();
    }

    @Test
    void adminCanRunRetentionOnDemand() throws Exception {
        mockMvc.perform(post("/api/v1/privacy/retention/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymizedGuests").isNumber())
                .andExpect(jsonPath("$.retentionDays").isNumber());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void receptionistCannotRunRetention() throws Exception {
        mockMvc.perform(post("/api/v1/privacy/retention/run")).andExpect(status().isForbidden());
    }
}
