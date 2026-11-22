package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.audit.AuditAction;
import br.com.fastcheckin.domain.audit.AuditEntry;
import br.com.fastcheckin.domain.audit.AuditService;
import br.com.fastcheckin.domain.consent.ConsentPurpose;
import br.com.fastcheckin.domain.consent.ConsentRepository;
import br.com.fastcheckin.domain.fnrh.FnrhRegistrationRepository;
import br.com.fastcheckin.domain.guest.GuestRepository;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import br.com.fastcheckin.domain.hotel.HotelService;
import br.com.fastcheckin.domain.ocr.OcrClient;
import br.com.fastcheckin.domain.ocr.OcrException;
import br.com.fastcheckin.domain.ocr.OcrReading;
import br.com.fastcheckin.domain.stay.StayRepository;
import br.com.fastcheckin.domain.stay.StayService;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Check-in pela foto do documento, com o serviço de OCR simulado. Usa apenas
 * dados sintéticos e uma "imagem" de mentira: o OCR de verdade é testado no
 * ocr-servico. O limite de tamanho do teste é de 100 bytes.
 */
@SpringBootTest(properties = "app.ocr.max-image-bytes=100")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "RECEPCIONISTA")
class FnrhDocumentApiTest {

    private static final byte[] IMAGE = "imagem-ficticia-de-teste".getBytes(StandardCharsets.UTF_8);
    private static final String URL = "/api/v1/fnrh-registrations/from-document";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OcrClient ocrClient;

    @Autowired
    private HotelService hotelService;

    @Autowired
    private StayService stayService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private FnrhRegistrationRepository fnrhRegistrationRepository;

    @Autowired
    private StayRepository stayRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private HotelRepository hotelRepository;

    private UUID stayId;

    @BeforeEach
    void createStay() {
        UUID hotelId = hotelService.register("Hotel " + UUID.randomUUID()).getId();
        stayId = stayService.schedule(hotelId, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 5)).getId();
    }

    @AfterEach
    void cleanUp() {
        fnrhRegistrationRepository.deleteAll();
        stayRepository.deleteAll();
        guestRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    private static OcrReading reading(String cpf, String dateOfBirth, boolean needsReview, List<String> reasons) {
        return new OcrReading(
                "PESSOA DE TESTE", cpf, dateOfBirth, "CNH", "TESTE-123", needsReview, reasons, "tesseract");
    }

    private static OcrReading cleanReading() {
        return reading("11144477735", "1985-09-03", false, List.of());
    }

    private ResultActions upload(UUID stay, String consentAccepted) throws Exception {
        MockHttpServletRequestBuilder request = post(URL)
                .param("stayId", stay.toString())
                .contentType(MediaType.IMAGE_PNG)
                .content(IMAGE);
        if (consentAccepted != null) {
            request.param("consentAccepted", consentAccepted);
        }
        return mockMvc.perform(request);
    }

    @Test
    void createsGuestAndPendingFnrhFromDocument() throws Exception {
        when(ocrClient.read(any(), anyString())).thenReturn(cleanReading());

        String body = upload(stayId, "true")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fnrh.status").value("PENDING"))
                .andExpect(jsonPath("$.fnrh.stayId").value(stayId.toString()))
                .andExpect(jsonPath("$.fnrh.reviewRequired").value(false))
                .andExpect(jsonPath("$.reviewRequired").value(false))
                .andExpect(jsonPath("$.reviewReasons").isEmpty())
                .andExpect(jsonPath("$.extracted.fullName").value("PESSOA DE TESTE"))
                .andExpect(jsonPath("$.extracted.cpf").value("11144477735"))
                .andExpect(jsonPath("$.extracted.dateOfBirth").value("1985-09-03"))
                .andExpect(jsonPath("$.extracted.documentType").value("CNH"))
                .andReturn().getResponse().getContentAsString();

        assertThat(guestRepository.count()).isEqualTo(1);
        assertThat(fnrhRegistrationRepository.count()).isEqualTo(1);
        UUID guestId = UUID.fromString(JsonPath.read(body, "$.fnrh.guestId"));
        assertThat(consentRepository.existsByGuestIdAndPurposeAndRevokedAtIsNull(
                guestId, ConsentPurpose.CHECKIN_FNRH)).isTrue();
    }

    @Test
    void flaggedReadingNeedsReviewUntilTheReceptionistConfirms() throws Exception {
        when(ocrClient.read(any(), anyString())).thenReturn(
                reading("11144477735", "1985-09-03", true, List.of("CPF com dígitos verificadores inválidos")));

        String body = upload(stayId, "true")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fnrh.status").value("PENDING"))
                .andExpect(jsonPath("$.fnrh.reviewRequired").value(true))
                .andExpect(jsonPath("$.reviewReasons[0]").value("CPF com dígitos verificadores inválidos"))
                .andReturn().getResponse().getContentAsString();
        String fnrhId = JsonPath.read(body, "$.fnrh.id");

        mockMvc.perform(post("/api/v1/fnrh-registrations/" + fnrhId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewRequired").value(false));
    }

    @Test
    void recordsCreationAndConfirmationInTheAuditTrail() throws Exception {
        when(ocrClient.read(any(), anyString())).thenReturn(cleanReading());

        String body = upload(stayId, "true").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID fnrhId = UUID.fromString(JsonPath.read(body, "$.fnrh.id"));
        mockMvc.perform(post("/api/v1/fnrh-registrations/" + fnrhId + "/confirm")).andExpect(status().isOk());

        List<AuditEntry> entries = auditService.recent(fnrhId, 10);
        assertThat(entries).extracting(AuditEntry::getResourceType).containsOnly(AuditService.FNRH);
        assertThat(entries).extracting(AuditEntry::getAction)
                .contains(AuditAction.CREATE, AuditAction.UPDATE);
    }

    @Test
    void refusesToProcessWithoutConsentAndDoesNotCallTheOcr() throws Exception {
        upload(stayId, null).andExpect(status().isUnprocessableEntity());
        upload(stayId, "false").andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(ocrClient);
        assertThat(guestRepository.count()).isZero();
        assertThat(fnrhRegistrationRepository.count()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownStayWithoutCallingTheOcr() throws Exception {
        upload(UUID.randomUUID(), "true").andExpect(status().isNotFound());

        verifyNoInteractions(ocrClient);
        assertThat(guestRepository.count()).isZero();
    }

    @Test
    void savesNothingWhenTheOcrMissesAField() throws Exception {
        when(ocrClient.read(any(), anyString())).thenReturn(reading(null, "1985-09-03", true, List.of()));

        upload(stayId, "true")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details", hasItem("codigo: DOCUMENTO_INCOMPLETO")))
                .andExpect(jsonPath("$.details", hasItem("campo não lido: CPF")));

        assertThat(guestRepository.count()).isZero();
        assertThat(fnrhRegistrationRepository.count()).isZero();
    }

    @Test
    void treatsAnInvalidOrFutureBirthDateAsMissing() throws Exception {
        when(ocrClient.read(any(), anyString())).thenReturn(reading("11144477735", "2999-01-01", true, List.of()));
        upload(stayId, "true")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details", hasItem("campo não lido: data de nascimento")));

        when(ocrClient.read(any(), anyString())).thenReturn(reading("11144477735", "03/09/1985", true, List.of()));
        upload(stayId, "true")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details", hasItem("campo não lido: data de nascimento")));

        assertThat(guestRepository.count()).isZero();
    }

    @Test
    void passesOcrServiceFailuresToTheClientWithoutSavingAnything() throws Exception {
        doThrow(new OcrException(503, "OCR_INDISPONIVEL", "O serviço de leitura não está disponível."))
                .when(ocrClient).read(any(), anyString());
        upload(stayId, "true")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.details", hasItem("codigo: OCR_INDISPONIVEL")));

        doThrow(new OcrException(422, "TEXTO_NAO_ENCONTRADO", "Não encontrei texto legível."))
                .when(ocrClient).read(any(), anyString());
        upload(stayId, "true")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Não encontrei texto legível."));

        assertThat(guestRepository.count()).isZero();
        assertThat(fnrhRegistrationRepository.count()).isZero();
    }

    @Test
    void rejectsBodyThatIsNotAnImage() throws Exception {
        mockMvc.perform(post(URL)
                        .param("stayId", stayId.toString())
                        .param("consentAccepted", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.details", hasItem("codigo: FORMATO_NAO_SUPORTADO")));

        verifyNoInteractions(ocrClient);
    }

    @Test
    void rejectsEmptyAndTooLargeImages() throws Exception {
        mockMvc.perform(post(URL)
                        .param("stayId", stayId.toString())
                        .param("consentAccepted", "true")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[0]))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post(URL)
                        .param("stayId", stayId.toString())
                        .param("consentAccepted", "true")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[101]))
                .andExpect(status().isPayloadTooLarge());

        verifyNoInteractions(ocrClient);
    }

    @Test
    void confirmingAnUnknownFnrhReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/fnrh-registrations/" + UUID.randomUUID() + "/confirm"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void requiresLogin() throws Exception {
        upload(stayId, "true").andExpect(status().isUnauthorized());

        verifyNoInteractions(ocrClient);
    }
}
