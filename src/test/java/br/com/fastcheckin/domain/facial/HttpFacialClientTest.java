package br.com.fastcheckin.domain.facial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cliente HTTP do serviço facial contra um servidor local de mentira. Sem imagens reais. */
class HttpFacialClientTest {

    private static final byte[] DOCUMENT = "foto-do-documento-ficticia".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SELFIE = "selfie-ficticia".getBytes(StandardCharsets.UTF_8);

    private static final String OK_BODY =
            "{\"resultado\":\"revisar\",\"precisa_revisao\":true,\"modelo\":\"ArcFace\",\"razao\":0.93}";

    private HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> receivedType = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void start(int status, String body, long delayMillis) throws IOException {
        HttpHandler handler = (HttpExchange exchange) -> {
            try {
                receivedKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
                receivedType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                receivedPath.set(exchange.getRequestURI().getPath());
                receivedBody.set(exchange.getRequestBody().readAllBytes());
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, out.length);
                try (OutputStream stream = exchange.getResponseBody()) {
                    stream.write(out);
                }
            } catch (Exception ignored) {
                // o cliente pode ter desistido (teste de timeout)
            } finally {
                exchange.close();
            }
        };
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/comparar", handler);
        server.start();
    }

    private HttpFacialClient client(String apiKey, long timeoutSeconds) {
        return new HttpFacialClient(
                objectMapper,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                apiKey,
                2,
                timeoutSeconds
        );
    }

    private void assertFails(HttpFacialClient client, int status, String code) {
        assertThatThrownBy(() -> client.compare(DOCUMENT, SELFIE))
                .isInstanceOfSatisfying(FacialException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }

    @Test
    void sendsBothImagesInBase64AndTheKeyAndReadsTheResult() throws Exception {
        start(200, OK_BODY, 0);

        FacialComparison comparison = client("chave-de-teste", 5).compare(DOCUMENT, SELFIE);

        assertThat(receivedPath.get()).isEqualTo("/comparar");
        assertThat(receivedKey.get()).isEqualTo("chave-de-teste");
        assertThat(receivedType.get()).isEqualTo("application/json");
        JsonNode sent = objectMapper.readTree(receivedBody.get());
        assertThat(Base64.getDecoder().decode(sent.path("documento").asText())).isEqualTo(DOCUMENT);
        assertThat(Base64.getDecoder().decode(sent.path("selfie").asText())).isEqualTo(SELFIE);
        assertThat(comparison.result()).isEqualTo(FacialResult.REVISAR);
        assertThat(comparison.ratio()).isEqualTo(0.93);
        assertThat(comparison.model()).isEqualTo("ArcFace");
        assertThat(comparison.needsReview()).isTrue();
    }

    @Test
    void omitsTheKeyHeaderWhenNoKeyIsConfigured() throws Exception {
        start(200, OK_BODY, 0);

        client("", 5).compare(DOCUMENT, SELFIE);

        assertThat(receivedKey.get()).isNull();
    }

    @Test
    void readsAResultWithoutRatioButWithReason() throws Exception {
        start(200, "{\"resultado\":\"revisar\",\"motivo\":\"rosto não detectado em selfie\",\"modelo\":\"ArcFace\"}", 0);

        FacialComparison comparison = client("", 5).compare(DOCUMENT, SELFIE);

        assertThat(comparison.ratio()).isNull();
        assertThat(comparison.reason()).isEqualTo("rosto não detectado em selfie");
    }

    @Test
    void compatibleDoesNotNeedReview() throws Exception {
        start(200, "{\"resultado\":\"compativel\",\"razao\":0.4,\"modelo\":\"ArcFace\"}", 0);

        FacialComparison comparison = client("", 5).compare(DOCUMENT, SELFIE);

        assertThat(comparison.result()).isEqualTo(FacialResult.COMPATIVEL);
        assertThat(comparison.needsReview()).isFalse();
    }

    @Test
    void keepsTheServiceMessageForImageProblems() throws Exception {
        start(422, "{\"codigo\":\"IMAGEM_INVALIDA\",\"mensagem\":\"A imagem 'selfie' não está em Base64 válido.\"}", 0);

        assertThatThrownBy(() -> client("", 5).compare(DOCUMENT, SELFIE))
                .isInstanceOfSatisfying(FacialException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(422);
                    assertThat(exception.getCode()).isEqualTo("IMAGEM_INVALIDA");
                    assertThat(exception.getMessage()).isEqualTo("A imagem 'selfie' não está em Base64 válido.");
                });
    }

    @Test
    void mapsServiceErrorsToStableCodes() throws Exception {
        start(401, "{\"codigo\":\"NAO_AUTORIZADO\",\"mensagem\":\"x\"}", 0);
        assertFails(client("errada", 5), 502, "FACIAL_NAO_AUTORIZADO");
        stopServer();

        start(429, "{\"codigo\":\"SERVICO_OCUPADO\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 503, "FACIAL_OCUPADO");
        stopServer();

        start(503, "{\"codigo\":\"X\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 503, "FACIAL_INDISPONIVEL");
        stopServer();

        start(500, "{\"codigo\":\"ERRO_INTERNO\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 502, "FACIAL_ERRO");
    }

    @Test
    void failsClearlyWhenTheResponseIsNotUnderstood() throws Exception {
        start(200, "isto não é json", 0);
        assertFails(client("", 5), 502, "FACIAL_RESPOSTA_INVALIDA");
        stopServer();

        start(200, "{\"resultado\":\"talvez\"}", 0);
        assertFails(client("", 5), 502, "FACIAL_RESPOSTA_INVALIDA");
    }

    @Test
    void reportsServiceUnavailableWhenNothingIsListening() throws Exception {
        start(200, OK_BODY, 0);
        HttpFacialClient client = client("", 5);
        stopServer();

        assertFails(client, 503, "FACIAL_INDISPONIVEL");
    }

    @Test
    void givesUpWhenTheServiceTakesTooLong() throws Exception {
        start(200, OK_BODY, 3000);

        assertFails(client("", 1), 504, "FACIAL_DEMOROU");
    }
}
