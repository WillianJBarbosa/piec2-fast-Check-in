package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.hotel.HotelService;
import br.com.fastcheckin.domain.stay.StayRepository;
import br.com.fastcheckin.domain.stay.StayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa apenas dados sintéticos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ConsentApiTest {

    private static final String GUEST_FIELDS = """
            "fullName":"Pessoa de Teste","cpf":"12345678901","dateOfBirth":"1990-04-12",
            "documentType":"CNH","documentNumber":"TESTE-123\"""";

    private static final String WITH_CONSENT = "{" + GUEST_FIELDS + ",\"consentAccepted\":true}";
    private static final String WITHOUT_CONSENT = "{" + GUEST_FIELDS + "}";
    private static final String REFUSED_CONSENT = "{" + GUEST_FIELDS + ",\"consentAccepted\":false}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private StayService stayService;

    @AfterEach
    void cleanUp() {
        fnrhRegistrationRepository.deleteAll();
        stayRepository.deleteAll();
        guestRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private String createGuest() throws Exception {
        String json = mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WITH_CONSENT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String purpose(String name) {
        return "{\"purpose\":\"" + name + "\"}";
    }

    @Test
    void rejectsGuestWithoutOrWithRefusedConsent() throws Exception {
        long before = guestRepository.count();

        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WITHOUT_CONSENT))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFUSED_CONSENT))
                .andExpect(status().isUnprocessableEntity());

        assertThat(guestRepository.count()).isEqualTo(before);
    }

    @Test
    void recordsCheckinConsentWhenTheGuestIsCreated() throws Exception {
        String id = createGuest();

        mockMvc.perform(get("/api/v1/guests/" + id + "/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purpose").value("CHECKIN_FNRH"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$[0].recordedBy").value("user"));
    }

    @Test
    void grantsAndRevokesFacialConsentKeepingTheHistory() throws Exception {
        String id = createGuest();

        mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purpose("BIOMETRIA_FACIAL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purpose").value("BIOMETRIA_FACIAL"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(delete("/api/v1/guests/" + id + "/consents/BIOMETRIA_FACIAL"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/guests/" + id + "/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].purpose").value("BIOMETRIA_FACIAL"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].revokedAt").exists());

        mockMvc.perform(delete("/api/v1/guests/" + id + "/consents/BIOMETRIA_FACIAL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantingTwiceKeepsASingleActiveConsent() throws Exception {
        String id = createGuest();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(purpose("BIOMETRIA_FACIAL")))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/guests/" + id + "/consents"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unknownGuestAndInvalidPurposeAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/guests/" + UUID.randomUUID() + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purpose("BIOMETRIA_FACIAL")))
                .andExpect(status().isNotFound());

        String id = createGuest();
        mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purpose("OUTRA_COISA")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fnrhRequiresAnActiveCheckinConsent() throws Exception {
        String guestId = createGuest();
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        UUID stayId = stayService.schedule(hotelId, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 5)).getId();
        String fnrh = "{\"stayId\":\"" + stayId + "\",\"guestId\":\"" + guestId + "\"}";

        mockMvc.perform(delete("/api/v1/guests/" + guestId + "/consents/CHECKIN_FNRH"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fnrh))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/guests/" + guestId + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purpose("CHECKIN_FNRH")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fnrh))
                .andExpect(status().isCreated());
    }

    @Test
    void consentChangesAreAudited() throws Exception {
        String id = createGuest();
        mockMvc.perform(post("/api/v1/guests/" + id + "/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purpose("BIOMETRIA_FACIAL")))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/guests/" + id + "/consents/BIOMETRIA_FACIAL"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/audit").param("guestId", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='CONSENT_GRANTED')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='CONSENT_REVOKED')]").isNotEmpty());
    }
}
