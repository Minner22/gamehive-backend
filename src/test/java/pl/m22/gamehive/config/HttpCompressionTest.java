package pl.m22.gamehive.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GH-143: kompresja odpowiedzi ({@code server.compression} w {@code application.yml}).
 *
 * <p>Kompresję robi konektor Tomcata, a MockMvc go omija — dlatego test startuje prawdziwy serwer
 * ({@code RANDOM_PORT}) i woła go zwykłym {@link HttpClient} z JDK, który <b>nie rozpakowuje gzipa sam</b>,
 * więc widać faktyczny nagłówek {@code Content-Encoding} i surowe bajty.
 *
 * <p>Uwaga przy rozbudowie: serwer działa w innym wątku niż test, więc {@code @Transactional} na teście
 * <b>nie</b> wycofa zmian we wspólnym H2 — test celowo tylko czyta i nie wymaga tokenu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpCompressionTest {

    private static final String GZIP = "gzip";

    @LocalServerPort
    int port;

    @MockitoBean
    JavaMailSender mailSender;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("Duża odpowiedź JSON + Accept-Encoding: gzip -> skompresowana, po rozpakowaniu poprawny JSON")
    void largeJson_withAcceptEncoding_isGzipped() throws Exception {
        HttpResponse<byte[]> gzipped = get("/v3/api-docs", true);
        HttpResponse<byte[]> plain = get("/v3/api-docs", false);

        assertThat(gzipped.statusCode()).isEqualTo(200);
        assertThat(gzipped.headers().firstValue("Content-Encoding")).hasValue(GZIP);

        String body = gunzip(gzipped.body());
        assertThat(body).contains("\"openapi\"").contains("GameHive API");
        // ta sama treść co bez kompresji, ale na łączu wyraźnie mniej bajtów
        assertThat(body).isEqualTo(new String(plain.body(), StandardCharsets.UTF_8));
        assertThat(gzipped.body().length).isLessThan(plain.body().length / 2);
    }

    @Test
    @DisplayName("Bez Accept-Encoding -> zwykły, nieskompresowany JSON (żaden klient się nie psuje)")
    void largeJson_withoutAcceptEncoding_isPlain() throws Exception {
        HttpResponse<byte[]> response = get("/v3/api-docs", false);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"openapi\"");
    }

    @Test
    @DisplayName("Mała odpowiedź kontrolera (ApiError z GlobalExceptionHandler) -> TEŻ skompresowana: próg jej nie dotyczy")
    void smallMvcResponse_isGzippedDespiteThreshold() throws Exception {
        // Świadoma decyzja GH-143: Spring MVC flushuje odpowiedź przed jej końcem, więc idzie ona jako
        // Transfer-Encoding: chunked bez Content-Length, a Tomcat stosuje min-response-size tylko przy znanej
        // długości. Każda odpowiedź kontrolera jest więc kompresowana, także ta z tokenem z /auth/login
        // (BREACH tu nie działa: login nie odbija danych atakującego, cookie refresh jest SameSite=Strict).
        // Gdyby ktoś "naprawił" próg (np. filtrem ustawiającym Content-Length), ten test o tym przypomni.
        HttpResponse<byte[]> response = post("/api/v1/auth/login",
                "{\"email\":\"nobody@example.com\",\"password\":\"whatever123\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Encoding")).hasValue(GZIP);
        assertThat(gunzip(response.body())).contains("\"errorCode\":\"INVALID_CREDENTIALS\"");
    }

    @Test
    @DisplayName("Mała odpowiedź o znanej długości (401 z entry pointu Security) -> bez kompresji, próg działa")
    void smallResponseWithKnownLength_belowThreshold_isNotGzipped() throws Exception {
        // Entry point pisze całe ciało naraz do bufora, więc Tomcat zna Content-Length i próg 2 KB działa.
        // To jedyna klasa odpowiedzi (obok zasobów statycznych), na którą min-response-size ma wpływ.
        HttpResponse<byte[]> response = get("/api/v1/collection/games/ids", true);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("\"errorCode\":\"ACCESS_DENIED\"");
    }

    private HttpResponse<byte[]> get(String path, boolean acceptGzip) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (acceptGzip) {
            request.header("Accept-Encoding", GZIP);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> post(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", GZIP)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
