package br.com.fastcheckin.domain.ocr;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cliente HTTP do OCR contra um servidor local de mentira. Sem dados reais. */
class HttpOcrClientTest {

    private static final byte[] IMAGE = "imagem-ficticia-de-teste".getBytes(StandardCharsets.UTF_8);

    private static final String OK_BODY = """
            {"tipo_documento":"CNH",
             "campos":{"nome":{"valor":"PESSOA DE TESTE","origem":"rotulo"}},
             "precisa_revisao":true,
             "motivos":["CPF com dígitos verificadores inválidos"],
             "motor":"tesseract",
             "corpo_guest":{"fullName":"PESSOA DE TESTE","cpf":"11144477735","dateOfBirth":"1985-09-03",
                            "documentType":"CNH","documentNumber":"TESTE-123"}}
            """;

    private HttpServer server;
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private final AtomicReference<String> receivedType = new AtomicReference<>();
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
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
                receivedQuery.set(exchange.getRequestURI().getRawQuery());
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
        server.createContext("/ocr", handler);
        server.start();
    }

    private HttpOcrClient client(String apiKey, long timeoutSeconds) {
        return new HttpOcrClient(
                new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                apiKey,
                "auto",
                2,
                timeoutSeconds
        );
    }

    private void assertFails(HttpOcrClient client, int status, String code) {
        assertThatThrownBy(() -> client.read(IMAGE, "image/png"))
                .isInstanceOfSatisfying(OcrException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getCode()).isEqualTo(code);
                });
    }

    @Test
    void sendsTheImageAndTheKeyAndReadsTheGuestBody() throws Exception {
        start(200, OK_BODY, 0);

        OcrReading reading = client("chave-de-teste", 5).read(IMAGE, "image/png");

        assertThat(receivedKey.get()).isEqualTo("chave-de-teste");
        assertThat(receivedType.get()).isEqualTo("image/png");
        assertThat(receivedQuery.get()).isEqualTo("motor=auto");
        assertThat(receivedBody.get()).isEqualTo(IMAGE);
        assertThat(reading.fullName()).isEqualTo("PESSOA DE TESTE");
        assertThat(reading.cpf()).isEqualTo("11144477735");
        assertThat(reading.dateOfBirth()).isEqualTo("1985-09-03");
        assertThat(reading.documentType()).isEqualTo("CNH");
        assertThat(reading.documentNumber()).isEqualTo("TESTE-123");
        assertThat(reading.needsReview()).isTrue();
        assertThat(reading.reasons()).containsExactly("CPF com dígitos verificadores inválidos");
        assertThat(reading.engine()).isEqualTo("tesseract");
    }

    @Test
    void omitsTheKeyHeaderWhenNoKeyIsConfigured() throws Exception {
        start(200, OK_BODY, 0);

        client("", 5).read(IMAGE, "image/png");

        assertThat(receivedKey.get()).isNull();
    }

    @Test
    void treatsMissingFieldsAsNullAndStillAsNeedingReview() throws Exception {
        start(200, "{\"corpo_guest\":{\"fullName\":null}}", 0);

        OcrReading reading = client("", 5).read(IMAGE, "image/png");

        assertThat(reading.fullName()).isNull();
        assertThat(reading.cpf()).isNull();
        assertThat(reading.needsReview()).isTrue();
    }

    @Test
    void keepsTheServiceMessageForImageProblems() throws Exception {
        start(422, "{\"codigo\":\"TEXTO_NAO_ENCONTRADO\",\"mensagem\":\"Fotografe o documento inteiro.\"}", 0);

        assertThatThrownBy(() -> client("", 5).read(IMAGE, "image/png"))
                .isInstanceOfSatisfying(OcrException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(422);
                    assertThat(exception.getCode()).isEqualTo("TEXTO_NAO_ENCONTRADO");
                    assertThat(exception.getMessage()).isEqualTo("Fotografe o documento inteiro.");
                });
    }

    @Test
    void mapsServiceErrorsToStableCodes() throws Exception {
        start(401, "{\"codigo\":\"NAO_AUTORIZADO\",\"mensagem\":\"x\"}", 0);
        assertFails(client("errada", 5), 502, "OCR_NAO_AUTORIZADO");
        stopServer();

        start(429, "{\"codigo\":\"SERVICO_OCUPADO\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 503, "OCR_OCUPADO");
        stopServer();

        start(503, "{\"codigo\":\"MOTOR_INDISPONIVEL\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 503, "OCR_INDISPONIVEL");
        stopServer();

        start(500, "{\"codigo\":\"FALHA_NO_OCR\",\"mensagem\":\"x\"}", 0);
        assertFails(client("", 5), 502, "OCR_ERRO");
    }

    @Test
    void failsClearlyWhenTheResponseIsNotJson() throws Exception {
        start(200, "isto não é json", 0);

        assertFails(client("", 5), 502, "OCR_RESPOSTA_INVALIDA");
    }

    @Test
    void reportsServiceUnavailableWhenNothingIsListening() throws Exception {
        start(200, OK_BODY, 0);
        HttpOcrClient client = client("", 5);
        stopServer();

        assertFails(client, 503, "OCR_INDISPONIVEL");
    }

    @Test
    void givesUpWhenTheServiceTakesTooLong() throws Exception {
        start(200, OK_BODY, 3000);

        assertFails(client("", 1), 504, "OCR_DEMOROU");
    }
}
