package br.com.fastcheckin.domain.facial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;

/**
 * Cliente HTTP do serviço facial (POST /comparar com as duas imagens em Base64
 * num JSON). Não grava as imagens, não registra o corpo e só loga o tipo da falha.
 */
@Component
public class HttpFacialClient implements FacialClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFacialClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    public HttpFacialClient(
            ObjectMapper objectMapper,
            @Value("${app.facial.url:http://127.0.0.1:8002}") String url,
            @Value("${app.facial.api-key:}") String apiKey,
            @Value("${app.facial.connect-timeout-seconds:3}") long connectTimeoutSeconds,
            @Value("${app.facial.timeout-seconds:60}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public FacialComparison compare(byte[] documentImage, byte[] selfieImage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("documento", Base64.getEncoder().encodeToString(documentImage));
        payload.put("selfie", Base64.getEncoder().encodeToString(selfieImage));

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(baseUrl + "/comparar"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)));
        } catch (IOException exception) {
            throw new FacialException(500, "FACIAL_ERRO", "Não foi possível montar a requisição de comparação.");
        }
        if (!apiKey.isBlank()) {
            builder.header("X-Api-Key", apiKey);
        }

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpConnectTimeoutException exception) {
            log.warn("Serviço facial não respondeu à conexão a tempo");
            throw unavailable();
        } catch (HttpTimeoutException exception) {
            log.warn("Serviço facial demorou mais que o limite para responder");
            throw new FacialException(504, "FACIAL_DEMOROU",
                    "O serviço de comparação facial demorou demais para responder. Tente de novo.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            log.warn("Serviço facial inacessível ({})", exception.getClass().getSimpleName());
            throw unavailable();
        }

        if (response.statusCode() == 200) {
            return parse(response.body());
        }
        log.warn("Serviço facial respondeu {}", response.statusCode());
        throw fromErrorResponse(response.statusCode(), response.body());
    }

    private FacialComparison parse(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            FacialResult result = FacialResult.valueOf(root.path("resultado").asText("").toUpperCase());
            JsonNode ratio = root.path("razao");
            return new FacialComparison(
                    result,
                    ratio.isNumber() ? ratio.asDouble() : null,
                    text(root, "motivo"),
                    text(root, "modelo")
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new FacialException(502, "FACIAL_RESPOSTA_INVALIDA",
                    "O serviço de comparação facial devolveu uma resposta que não consegui entender.");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private FacialException fromErrorResponse(int status, byte[] body) {
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
            case 401 -> new FacialException(502, "FACIAL_NAO_AUTORIZADO",
                    "A API não conseguiu se autenticar no serviço de comparação facial. Avise o administrador.");
            case 413 -> new FacialException(413, orDefault(code, "IMAGEM_GRANDE_DEMAIS"),
                    orDefault(message, "Uma das imagens é grande demais. Reduza a resolução da foto."));
            case 415 -> new FacialException(415, orDefault(code, "FORMATO_NAO_SUPORTADO"),
                    orDefault(message, "Formato não suportado. Envie JPEG, PNG, WEBP ou BMP."));
            case 422 -> new FacialException(422, orDefault(code, "IMAGEM_ILEGIVEL"),
                    orDefault(message, "Não consegui ler uma das imagens. Tire a foto de novo."));
            case 429 -> new FacialException(503, "FACIAL_OCUPADO",
                    "O serviço de comparação facial está ocupado. Tente de novo em instantes.");
            case 503 -> unavailable();
            default -> new FacialException(502, "FACIAL_ERRO",
                    "O serviço de comparação facial falhou. Tente de novo ou confira o hóspede manualmente.");
        };
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static FacialException unavailable() {
        return new FacialException(503, "FACIAL_INDISPONIVEL",
                "O serviço de comparação facial não está disponível no momento. Tente de novo ou confira o hóspede manualmente.");
    }
}
