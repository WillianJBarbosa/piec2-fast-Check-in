package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.user.AppUser;
import br.com.fastcheckin.domain.user.AppUserRepository;
import br.com.fastcheckin.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiTest {

    private static final String PASSWORD = "senha-admin-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void createAdminAndLogin() throws Exception {
        userRepository.deleteAll();
        userRepository.saveAndFlush(new AppUser("admin", passwordEncoder.encode(PASSWORD), UserRole.ADMIN));
        adminToken = "Bearer " + loginToken("admin", PASSWORD);
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private String loginToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String userBody(String username, String password, String role) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"" + role + "\"}";
    }

    @Test
    void adminCreatesUserWithoutExposingPassword() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("Maria.Silva", "senha-forte-1", "RECEPCIONISTA")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value("maria.silva"))
                .andExpect(jsonPath("$.role").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void newUserCanLogIn() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("carlos", "senha-forte-2", "RECEPCIONISTA")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carlos\",\"password\":\"senha-forte-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECEPCIONISTA"));
    }

    @Test
    void listNeverExposesPasswordHashes() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("admin", "outra-senha-123", "ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("joana", "curta", "RECEPCIONISTA")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("pedro", "senha-forte-3", "SUPERUSUARIO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatedUserCannotLogInAnymore() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("ana", "senha-forte-4", "RECEPCIONISTA")))
                .andExpect(status().isCreated())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/v1/users/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"senha-forte-4\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatingUnknownUserReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID()).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }
}
