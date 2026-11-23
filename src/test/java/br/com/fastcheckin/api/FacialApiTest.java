package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditEntry;
import br.com.fastcheckin.domain.audit.AuditService;
import br.com.fastcheckin.domain.consent.ConsentPurpose;
import br.com.fastcheckin.domain.consent.ConsentService;
import br.com.fastcheckin.domain.facial.FacialClient;
import br.com.fastcheckin.domain.facial.FacialComparison;
import br.com.fastcheckin.domain.facial.FacialException;
import br.com.fastcheckin.domain.facial.FacialResult;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationService;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comparação facial com o serviço facial simulado. Usa apenas dados sintéticos e
 * "imagens" de mentira: a comparação de verdade é testada no facial-servico. O
 * limite de tamanho do teste é de 100 bytes.
 */
@SpringBootTest(properties = "app.facial.max-image-bytes=100")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "RECEPCIONISTA")
class FacialApiTest {

    private static final byte[] DOCUMENT = "foto-do-documento-ficticia".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SELFIE = "selfie-ficticia".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacialClient facialClient;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private StayService stayService;

    @Autowired
    private GuestService guestService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private FnrhRegistrationService fnrhRegistrationService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private HotelRepository hotelRepository;

    private UUID guestId;
    private UUID fnrhId;

    @BeforeEach
    void createFnrhWithFacialConsent() {
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        UUID stayId = stayService.schedule(hotelId, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 5)).getId();
        guestId = guestService.register(new GuestPersonalData(
                "PESSOA DE TESTE", "11144477735", LocalDate.of(1985, 9, 3), DocumentType.CNH, "TESTE-123")).getId();
        consentService.grant(guestId, ConsentPurpose.BIOMETRIA_FACIAL);
        fnrhId = fnrhRegistrationService.register(stayId, guestId).getId();
    }

    @AfterEach
    void cleanUp() {
        fnrhRegistrationRepository.deleteAll();
        stayRepository.deleteAll();
        guestRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private ResultActions faceCheck(UUID id, String documentImage, String selfieImage) throws Exception {
        String body = "{\"documentImage\":\"" + documentImage + "\",\"selfieImage\":\"" + selfieImage + "\"}";
        return mockMvc.perform(post("/api/v1/fnrh-registrations/" + id + "/face-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions faceCheck() throws Exception {
        return faceCheck(fnrhId, b64(DOCUMENT), b64(SELFIE));
    }

    private ResultActions confirmFace(UUID id) throws Exception {
        return mockMvc.perform(post("/api/v1/fnrh-registrations/" + id + "/face-check/confirm"));
    }

    private void facialServiceAnswers(FacialResult result, Double ratio, String reason) {
        when(facialClient.compare(any(), any())).thenReturn(new FacialComparison(result, ratio, reason, "ArcFace"));
    }

    @Test
    void compatibleResultIsStoredAndNeedsNoReview() throws Exception {
        facialServiceAnswers(FacialResult.COMPATIVEL, 0.5, null);

        faceCheck()
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fnrhId").value(fnrhId.toString()))
                .andExpect(jsonPath("$.result").value("COMPATIVEL"))
                .andExpect(jsonPath("$.ratio").value(0.5))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.confirmedManually").value(false));

        verify(facialClient).compare(DOCUMENT, SELFIE);
        mockMvc.perform(get("/api/v1/fnrh-registrations/" + fnrhId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facialResult").value("COMPATIVEL"))
                .andExpect(jsonPath("$.facialConfirmedManually").value(false));
    }

    @Test
    void differentResultDoesNotBlockAndTheReceptionistCanConfirmManually() throws Exception {
        facialServiceAnswers(FacialResult.DIFERENTE, 1.3, null);

        faceCheck()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DIFERENTE"))
                .andExpect(jsonPath("$.needsReview").value(true));

        confirmFace(fnrhId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facialResult").value("DIFERENTE"))
                .andExpect(jsonPath("$.facialConfirmedManually").value(true));
    }

    @Test
    void noFaceFoundGoesToReviewWithTheReasonAndNoRatio() throws Exception {
        facialServiceAnswers(FacialResult.REVISAR, null, "rosto não detectado em selfie");

        faceCheck()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("REVISAR"))
                .andExpect(jsonPath("$.ratio").doesNotExist())
                .andExpect(jsonPath("$.reason").value("rosto não detectado em selfie"))
                .andExpect(jsonPath("$.needsReview").value(true));
    }

    @Test
    void aNewComparisonCancelsAPreviousManualConfirmation() throws Exception {
        facialServiceAnswers(FacialResult.DIFERENTE, 1.3, null);
        faceCheck().andExpect(status().isOk());
        confirmFace(fnrhId).andExpect(jsonPath("$.facialConfirmedManually").value(true));

        facialServiceAnswers(FacialResult.COMPATIVEL, 0.4, null);
        faceCheck()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedManually").value(false));
    }

    @Test
    void cannotConfirmManuallyBeforeTheComparison() throws Exception {
        confirmFace(fnrhId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details", hasItem("codigo: VERIFICACAO_FACIAL_PENDENTE")));
    }

    @Test
    void refusesWithoutFacialConsentAndDoesNotCallTheService() throws Exception {
        consentService.revoke(guestId, ConsentPurpose.BIOMETRIA_FACIAL);

        faceCheck().andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(facialClient);
        mockMvc.perform(get("/api/v1/fnrh-registrations/" + fnrhId))
                .andExpect(jsonPath("$.facialResult").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownFnrhWithoutCallingTheService() throws Exception {
        faceCheck(UUID.randomUUID(), b64(DOCUMENT), b64(SELFIE)).andExpect(status().isNotFound());
        confirmFace(UUID.randomUUID()).andExpect(status().isNotFound());

        verifyNoInteractions(facialClient);
    }

    @Test
    void rejectsImagesThatAreNotValidBase64OrAreTooBig() throws Exception {
        faceCheck(fnrhId, "isto não é base64!!", b64(SELFIE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details", hasItem("codigo: IMAGEM_INVALIDA")));
        faceCheck(fnrhId, b64(DOCUMENT), b64(new byte[101]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.details", hasItem("codigo: IMAGEM_GRANDE_DEMAIS")));
        // vale para as duas imagens, não só para a selfie
        faceCheck(fnrhId, b64(new byte[101]), b64(SELFIE))
                .andExpect(status().isPayloadTooLarge());

        verifyNoInteractions(facialClient);
    }

    @Test
    void requiresBothImages() throws Exception {
        mockMvc.perform(post("/api/v1/fnrh-registrations/" + fnrhId + "/face-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentImage\":\"" + b64(DOCUMENT) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("selfieImage: a selfie é obrigatória")));

        verifyNoInteractions(facialClient);
    }

    @Test
    void passesServiceFailuresWithStableCodes() throws Exception {
        doThrow(new FacialException(503, "FACIAL_INDISPONIVEL", "Serviço fora do ar."))
                .when(facialClient).compare(any(), any());
        faceCheck()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.details", hasItem("codigo: FACIAL_INDISPONIVEL")));

        doThrow(new FacialException(422, "IMAGEM_ILEGIVEL", "Tire a foto de novo."))
                .when(facialClient).compare(any(), any());
        faceCheck()
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details", hasItem("codigo: IMAGEM_ILEGIVEL")));

        mockMvc.perform(get("/api/v1/fnrh-registrations/" + fnrhId))
                .andExpect(jsonPath("$.facialResult").doesNotExist());
    }

    @Test
    void recordsTheComparisonAndTheManualConfirmationInTheAuditTrail() throws Exception {
        facialServiceAnswers(FacialResult.REVISAR, 1.0, null);

        faceCheck().andExpect(status().isOk());
        confirmFace(fnrhId).andExpect(status().isOk());

        List<AuditEntry> entries = auditService.recent(fnrhId, 10);
        assertThat(entries).extracting(AuditEntry::getResourceType).containsOnly(AuditService.FNRH);
        assertThat(entries).extracting(AuditEntry::getAction).containsExactlyInAnyOrder(
                AuditAction.UPDATE, AuditAction.UPDATE);
    }

    @Test
    @WithAnonymousUser
    void requiresLogin() throws Exception {
        faceCheck().andExpect(status().isUnauthorized());
        confirmFace(fnrhId).andExpect(status().isUnauthorized());

        verifyNoInteractions(facialClient);
    }
}
