package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.guest.DocumentType;
import br.com.fastcheckin.domain.guest.GuestPersonalData;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.guest.GuestService;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.hotel.HotelService;
import br.com.fastcheckin.domain.stay.StayRepository;
import br.com.fastcheckin.domain.stay.StayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa apenas dados sintéticos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FnrhRegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private StayService stayService;

    @Autowired
    private GuestService guestService;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private HotelRepository hotelRepository;

    private UUID stayId;
    private UUID guestId;

    @BeforeEach
    void createStayAndGuest() {
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        stayId = stayService.schedule(hotelId, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 5)).getId();
        guestId = guestService.register(new GuestPersonalData(
                "Pessoa de Teste",
                "12345678901",
                LocalDate.of(1990, 4, 12),
                DocumentType.CNH,
                "TESTE-123"
        )).getId();
    }

    @AfterEach
    void cleanUp() {
        fnrhRegistrationRepository.deleteAll();
        stayRepository.deleteAll();
        guestRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private static String body(UUID stay, UUID guest) {
        return "{\"stayId\":\"" + stay + "\",\"guestId\":\"" + guest + "\"}";
    }

    private String createFnrh() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stayId, guestId)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsFnrhAsPending() throws Exception {
        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stayId, guestId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.guestId").value(guestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsDuplicateFnrhForSameStayAndGuest() throws Exception {
        createFnrh();

        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stayId, guestId)))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownStayOrGuest() throws Exception {
        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), guestId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stayId, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/fnrh-registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmsAndDeletesFnrh() throws Exception {
        String id = createFnrh();

        mockMvc.perform(put("/api/v1/fnrh-registrations/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/fnrh-registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/v1/fnrh-registrations/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/fnrh-registrations/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesToDeleteStayThatStillHasFnrh() throws Exception {
        createFnrh();

        mockMvc.perform(delete("/api/v1/stays/" + stayId))
                .andExpect(status().isConflict());
    }
}
