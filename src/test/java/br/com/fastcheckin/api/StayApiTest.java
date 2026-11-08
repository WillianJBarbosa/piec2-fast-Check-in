package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.hotel.Hotel;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.stay.StayRepository;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StayApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private StayRepository stayRepository;

    private UUID hotelId;

    @BeforeEach
    void createHotel() {
        hotelId = hotelRepository.save(new Hotel("Hotel " + UUID.randomUUID())).getId();
    }

    @AfterEach
    void cleanUp() {
        stayRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private static String stayBody(UUID hotel, String arrival, String departure) {
        return "{\"hotelId\":\"" + hotel + "\",\"arrivalDate\":\"" + arrival
                + "\",\"departureDate\":\"" + departure + "\"}";
    }

    private String createStay() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(hotelId, "2026-12-03", "2026-12-05")))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsStayAsPending() throws Exception {
        mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(hotelId, "2026-12-03", "2026-12-05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(hotelId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsDepartureNotAfterArrival() throws Exception {
        mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(hotelId, "2026-12-05", "2026-12-03")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(hotelId, "2026-12-05", "2026-12-05")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void returnsNotFoundWhenHotelDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/stays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stayBody(UUID.randomUUID(), "2026-12-03", "2026-12-05")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsStaysByHotel() throws Exception {
        createStay();

        mockMvc.perform(get("/api/v1/stays").param("hotelId", hotelId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hotelId").value(hotelId.toString()));

        mockMvc.perform(get("/api/v1/stays").param("hotelId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updatesAndDeletesStay() throws Exception {
        String id = createStay();

        mockMvc.perform(put("/api/v1/stays/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalDate\":\"2026-12-04\",\"departureDate\":\"2026-12-08\","
                                + "\"status\":\"CHECKED_IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivalDate").value("2026-12-04"))
                .andExpect(jsonPath("$.departureDate").value("2026-12-08"))
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));

        mockMvc.perform(delete("/api/v1/stays/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/stays/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidDatesOnUpdate() throws Exception {
        String id = createStay();

        mockMvc.perform(put("/api/v1/stays/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalDate\":\"2026-12-08\",\"departureDate\":\"2026-12-04\","
                                + "\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());
    }
}
