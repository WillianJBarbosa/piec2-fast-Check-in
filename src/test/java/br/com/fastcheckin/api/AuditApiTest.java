package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.guest.GuestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
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
@WithMockUser(username = "auditor", roles = "ADMIN")
class AuditApiTest {

    private static final String BODY = """
            {"fullName":"Pessoa de Teste","cpf":"12345678901","dateOfBirth":"1990-04-12",
             "documentType":"CNH","documentNumber":"TESTE-123","consentAccepted":true}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GuestRepository guestRepository;

    @AfterEach
    void cleanUp() {
        guestRepository.deleteAll();
    }

    private String createGuest() throws Exception {
        String json = mockMvc.perform(post("/api/v1/guests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions auditOf(String guestId) throws Exception {
        return mockMvc.perform(get("/api/v1/audit").param("guestId", guestId));
    }

    private int countOf(String guestId, String action) throws Exception {
        String json = auditOf(guestId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.split("\"action\":\"" + action + "\"", -1).length - 1;
    }

    @Test
    void recordsCreateReadUpdateAndDeleteWithTheUser() throws Exception {
        String id = createGuest();
        mockMvc.perform(get("/api/v1/guests/" + id)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/guests/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/guests/" + id)).andExpect(status().isNoContent());

        auditOf(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='CREATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='READ')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='UPDATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='DELETE')]").isNotEmpty())
                .andExpect(jsonPath("$[0].username").value("auditor"))
                .andExpect(jsonPath("$[0].resourceType").value("GUEST"));
    }

    @Test
    void listingGuestsAuditsEachGuestRead() throws Exception {
        String id = createGuest();
        int before = countOf(id, "READ");

        mockMvc.perform(get("/api/v1/guests")).andExpect(status().isOk());

        assertThat(countOf(id, "READ")).isEqualTo(before + 1);
    }

    @Test
    void auditTrailNeverContainsPersonalData() throws Exception {
        String id = createGuest();
        mockMvc.perform(get("/api/v1/guests/" + id)).andExpect(status().isOk());

        String json = auditOf(id).andReturn().getResponse().getContentAsString();

        assertThat(json)
                .doesNotContain("12345678901")
                .doesNotContain("Pessoa de Teste")
                .doesNotContain("TESTE-123");
    }

    @Test
    void failedReadsAreNotAudited() throws Exception {
        String unknown = "00000000-0000-0000-0000-000000000001";
        mockMvc.perform(get("/api/v1/guests/" + unknown)).andExpect(status().isNotFound());

        auditOf(unknown).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void receptionistCannotReadTheAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadTheAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
    }
}
