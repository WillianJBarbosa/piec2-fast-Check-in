package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.hotel.HotelRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confere os perfis: RECEPCIONISTA opera check-ins, mas não gerencia hotéis nem usuários (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessControlApiTest {

    private static final String PASSWORD = "senha-teste-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        userRepository.deleteAll();
        userRepository.saveAndFlush(new AppUser("admin", passwordEncoder.encode(PASSWORD), UserRole.ADMIN));
        userRepository.saveAndFlush(new AppUser("recepcao", passwordEncoder.encode(PASSWORD), UserRole.RECEPCIONISTA));
    }

    @AfterEach
    void cleanUp() {
        hotelRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String tokenOf(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return "Bearer " + json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void receptionistCanReadHotels() throws Exception {
        mockMvc.perform(get("/api/v1/hotels").header("Authorization", tokenOf("recepcao")))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCanOperateCheckInResources() throws Exception {
        String token = tokenOf("recepcao");
        mockMvc.perform(get("/api/v1/guests").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stays").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fnrh-registrations").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void receptionistCannotCreateHotel() throws Exception {
        mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", tokenOf("recepcao"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotel Proibido\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCannotDeleteHotel() throws Exception {
        mockMvc.perform(delete("/api/v1/hotels/" + UUID.randomUUID())
                        .header("Authorization", tokenOf("recepcao")))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistCannotManageUsers() throws Exception {
        String token = tokenOf("recepcao");
        mockMvc.perform(get("/api/v1/users").header("Authorization", token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso\",\"password\":\"senha-12345\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateHotel() throws Exception {
        mockMvc.perform(post("/api/v1/hotels")
                        .header("Authorization", tokenOf("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hotel Autorizado\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unknownPathsAlsoRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/nao-existe"))
                .andExpect(status().isUnauthorized());
    }
}
