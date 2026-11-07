package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.guest.GuestRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa apenas dados sintéticos, nunca documentos ou CPFs reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestApiTest {

    private static final String VALID_BODY = """
            {"fullName":"Pessoa de Teste","cpf":"12345678901","dateOfBirth":"1990-04-12",
             "documentType":"CNH","documentNumber":"TESTE-123"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository guestRepository;

    @AfterEach
    void cleanUp() {
        guestRepository.deleteAll();
    }

    private String createGuest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsGuestAndReturnsMaskedData() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Pessoa de Teste"))
                .andExpect(jsonPath("$.cpfMasked").value("***.***.***-01"))
                .andExpect(jsonPath("$.documentNumberMasked").value("******123"))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("12345678901")
                .doesNotContain("TESTE-123")
                .doesNotContain("v1:");
    }

    @Test
    void storesOnlyCiphertextInTheDatabase() throws Exception {
        createGuest();

        String stored = guestRepository.findAll().get(0).getPersonalDataCiphertext();
        assertThat(stored)
                .startsWith("v1:")
                .doesNotContain("Pessoa de Teste")
                .doesNotContain("12345678901");
    }

    @Test
    void rejectsInvalidCpfAndMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"","cpf":"123","documentType":"CNH","documentNumber":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void rejectsUnknownDocumentType() throws Exception {
        mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Pessoa de Teste","cpf":"12345678901","dateOfBirth":"1990-04-12",
                                 "documentType":"PASSAPORTE","documentNumber":"X1"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAndDeletesGuest() throws Exception {
        String id = createGuest();

        mockMvc.perform(put("/api/v1/guests/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Outra Pessoa de Teste","cpf":"10987654321","dateOfBirth":"1985-01-30",
                                 "documentType":"RG","documentNumber":"RG-0099"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Outra Pessoa de Teste"))
                .andExpect(jsonPath("$.cpfMasked").value("***.***.***-21"))
                .andExpect(jsonPath("$.documentType").value("RG"));

        mockMvc.perform(delete("/api/v1/guests/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/guests/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForUnknownGuest() throws Exception {
        mockMvc.perform(get("/api/v1/guests/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
