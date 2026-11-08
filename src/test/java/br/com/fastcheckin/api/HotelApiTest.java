package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.hotel.HotelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HotelApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @AfterEach
    void cleanUp() {
        hotelRepository.deleteAll();
    }

    private static String uniqueName() {
        return "Hotel " + UUID.randomUUID();
    }

    private static String body(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private String createHotel(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name)))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsAndReadsHotel() throws Exception {
        String name = uniqueName();

        mockMvc.perform(post("/api/v1/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(name));

        mockMvc.perform(get("/api/v1/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(name)));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/v1/hotels").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateName() throws Exception {
        String name = uniqueName();
        createHotel(name);

        mockMvc.perform(post("/api/v1/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesAndDeletesHotel() throws Exception {
        String id = createHotel(uniqueName());
        String newName = uniqueName();

        mockMvc.perform(put("/api/v1/hotels/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(newName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));

        mockMvc.perform(delete("/api/v1/hotels/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/hotels/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForUnknownHotel() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/nao-e-um-uuid"))
                .andExpect(status().isBadRequest());
    }
}
