package br.com.fastcheckin.domain.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP do serviço de OCR (POST /ocr com os bytes da imagem no corpo).
 * Não grava a imagem, não registra a resposta e só loga o tipo da falha.
 */
@Component
public class HttpOcrClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOcrClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String engine;
    private final Duration timeout;

    public HttpOcrClient(
            ObjectMapper objectMapper,
            @Value("${app.ocr.url:http://127.0.0.1:8001}") String url,
            @Value("${app.ocr.api-key:}") String apiKey,
            @Value("${app.ocr.engine:auto}") String engine,
            @Value("${app.ocr.connect-timeout-seconds:3}") long connectTimeoutSeconds,
            @Value("${app.ocr.timeout-seconds:30}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.engine = engine;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public OcrReading read(byte[] image, String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/ocr?motor=" + URLEncoder.encode(engine, StandardCharsets.UTF_8)))
                .timeout(timeout)
                .header("Content-Type", contentType == null ? "application/octet-stream" : contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(image));
        if (!apiKey.isBlank()) {
            builder.header("X-Api-Key", apiKey);
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpConnectTimeoutException exception) {
            log.warn("Serviço de OCR não respondeu à conexão a tempo");
            throw unavailable();
        } catch (HttpTimeoutException exception) {
            log.warn("Serviço de OCR demorou mais que o limite para responder");
            throw new OcrException(504, "OCR_DEMOROU",
                    "O serviço de leitura de documentos demorou demais para responder. Tente de novo.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            log.warn("Serviço de OCR inacessível ({})", exception.getClass().getSimpleName());
            throw unavailable();
        }

        if (response.statusCode() == 200) {
            return parse(response.body());
        }
        log.warn("Serviço de OCR respondeu {}", response.statusCode());
        throw fromErrorResponse(response.statusCode(), response.body());
    }

    private OcrReading parse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode guest = root.path("corpo_guest");
            List<String> reasons = new ArrayList<>();
            root.path("motivos").forEach(reason -> reasons.add(reason.asText()));
            return new OcrReading(
                    text(guest, "fullName"),
                    text(guest, "cpf"),
                    text(guest, "dateOfBirth"),
                    text(guest, "documentType"),
                    text(guest, "documentNumber"),
                    root.path("precisa_revisao").asBoolean(true),
                    reasons,
                    root.path("motor").asText("")
            );
        } catch (IOException exception) {
            throw new OcrException(502, "OCR_RESPOSTA_INVALIDA",
                    "O serviço de leitura de documentos devolveu uma resposta que não consegui entender.");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private OcrException fromErrorResponse(int status, byte[] body) {
        String code = null;
        String message = null;
        try {
            JsonNode node = objectMapper.readTree(body);
            code = node.path("codigo").asText(null);
            message = node.path("mensagem").asText(null);
        } catch (IOException exception) {
            // corpo que não é JSON: usa as mensagens padrão abaixo
        }
        return switch (status) {
            case 401 -> new OcrException(502, "OCR_NAO_AUTORIZADO",
                    "A API não conseguiu se autenticar no serviço de leitura de documentos. Avise o administrador.");
            case 413 -> new OcrException(413, orDefault(code, "IMAGEM_GRANDE_DEMAIS"),
                    orDefault(message, "A imagem é grande demais. Reduza a resolução da foto."));
            case 415 -> new OcrException(415, orDefault(code, "FORMATO_NAO_SUPORTADO"),
                    orDefault(message, "Formato não suportado. Envie JPEG, PNG, WEBP ou BMP."));
            case 422 -> new OcrException(422, orDefault(code, "IMAGEM_ILEGIVEL"),
                    orDefault(message, "Não consegui ler a imagem. Fotografe o documento de novo."));
            case 429 -> new OcrException(503, "OCR_OCUPADO",
                    "O serviço de leitura de documentos está ocupado. Tente de novo em instantes.");
            case 503 -> unavailable();
            default -> new OcrException(502, "OCR_ERRO",
                    "O serviço de leitura de documentos falhou. Tente de novo ou cadastre o hóspede manualmente.");
        };
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static OcrException unavailable() {
        return new OcrException(503, "OCR_INDISPONIVEL",
                "O serviço de leitura de documentos não está disponível no momento. Tente de novo ou cadastre o hóspede manualmente.");
    }
}
