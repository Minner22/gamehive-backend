package pl.m22.gamehive.common.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.logging.CorrelationIdFilter.*;

/**
 * GH-144: {@link CorrelationIdFilter} przyjmuje z nagłówka tylko {@code [A-Za-z0-9._-]{1,64}}, a resztę
 * podmienia na UUID. Za długa wartość gubiła wpisy audytu ({@code VARCHAR(64)}), a znaki sterujące trafiały
 * do każdej linii logu żądania.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Limit filtra nie przekracza długości kolumny correlation_id (64) w obu tabelach audytu")
    void maxLength_matchesAuditColumnLength() {
        assertThat(MAX_CORRELATION_ID_LENGTH).isEqualTo(64);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> zachowany")
    @ValueSource(strings = {
            "3f2b6c1e-8d4a-4f7b-9c2e-1a5d7e9b0c3f",   // UUID — format, który generujemy sami
            "test-corr-1",
            "req.42_A-b",
            "a"                                        // dolna granica
    })
    @DisplayName("Poprawny nagłówek -> zachowany bez zmian w MDC i w odpowiedzi")
    void validHeader_isKept(String header) throws Exception {
        Result result = run(header);

        assertThat(result.mdcInChain()).isEqualTo(header);
        assertThat(result.responseHeader()).isEqualTo(header);
    }

    @Test
    @DisplayName("Dokładnie 64 znaki -> zachowany (górna granica)")
    void exactlyMaxLength_isKept() throws Exception {
        String header = "a".repeat(MAX_CORRELATION_ID_LENGTH);

        Result result = run(header);

        assertThat(result.mdcInChain()).isEqualTo(header);
        assertThat(result.responseHeader()).isEqualTo(header);
    }

    @Test
    @DisplayName("65 znaków -> podmieniony na UUID (nie przycięty)")
    void oneOverMaxLength_isReplaced() throws Exception {
        assertReplacedWithUuid(run("a".repeat(MAX_CORRELATION_ID_LENGTH + 1)));
    }

    @Test
    @DisplayName("500 znaków -> podmieniony na UUID")
    void veryLongHeader_isReplaced() throws Exception {
        assertReplacedWithUuid(run("x".repeat(500)));
    }

    @Test
    @DisplayName("Brak nagłówka -> wygenerowany UUID")
    void missingHeader_isGenerated() throws Exception {
        assertReplacedWithUuid(run(null));
    }

    @Test
    @DisplayName("Pusty nagłówek -> wygenerowany UUID (wcześniej przechodził jako pusty string)")
    void emptyHeader_isGenerated() throws Exception {
        assertReplacedWithUuid(run(""));
    }

    @ParameterizedTest(name = "[{index}] znak niedozwolony -> podmieniony")
    @ValueSource(strings = {
            "abc\ninjected",          // nowa linia — log injection
            "abc\r\nX-Evil: 1",       // CRLF
            "abc\u001b[31mred",       // sekwencja ANSI
            "abc def",                // spacja
            "abc\tdef",               // tabulator
            "zażółć",                 // spoza ASCII
            "abc\"}",                 // cudzysłów / klamra (ucieczka z JSON-owego logu)
            "abc/def"
    })
    @DisplayName("Znaki spoza wzorca -> podmieniony na UUID")
    void disallowedCharacters_areReplaced(String header) throws Exception {
        assertReplacedWithUuid(run(header));
    }

    @Test
    @DisplayName("Każde żądanie bez nagłówka dostaje inny UUID")
    void generatedIds_areUnique() throws Exception {
        assertThat(run(null).mdcInChain()).isNotEqualTo(run(null).mdcInChain());
    }

    @Test
    @DisplayName("MDC jest czyszczone po filtrze, także gdy łańcuch rzuci wyjątek")
    void mdcIsCleared_evenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, "test-corr-1");
        FilterChain failing = (_, _) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(CORRELATION_ID)).isNull();
    }

    // ---------- GH-163: ERROR dispatch tego samego żądania dostaje TO SAMO id ----------

    @Test
    @DisplayName("Pierwsze przejście zapisuje id w atrybucie requestu (dla późniejszego ERROR dispatchu)")
    void firstPass_storesIdInRequestAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (_, _) -> { });

        assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE))
                .isEqualTo(response.getHeader(CORRELATION_ID_HEADER))
                .isNotNull();
    }

    @Test
    @DisplayName("ERROR dispatch po wyjątku z łańcucha -> filtr działa ponownie z TYM SAMYM id (bez nagłówka klienta)")
    void errorDispatch_reusesIdFromFirstPass() throws Exception {
        // Tomcat robi ERROR dispatch do /error dopiero PO wyjściu z pierwotnego łańcucha: MDC jest już
        // wyczyszczone, a znacznik "already filtered" zdjęty — ten sam obiekt requestu przechodzi filtr drugi raz.
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (_, _) -> {
            throw new ServletException("wyjątek z filtra, np. JwtAuthFilter");
        };
        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isInstanceOf(ServletException.class);
        String firstPassId = response.getHeader(CORRELATION_ID_HEADER);
        assertThat(MDC.get(CORRELATION_ID)).isNull();

        simulateErrorDispatch(request);
        AtomicReference<String> mdcOnErrorDispatch = new AtomicReference<>();
        filter.doFilter(request, response, (_, _) -> mdcOnErrorDispatch.set(MDC.get(CORRELATION_ID)));

        // bez atrybutu wygenerowałby tu drugi, inny UUID — log ścieżki błędu nie dałby się powiązać z odpowiedzią
        assertThat(mdcOnErrorDispatch.get()).isEqualTo(firstPassId);
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(firstPassId);
        assertThat(MDC.get(CORRELATION_ID)).isNull();
    }

    @Test
    @DisplayName("ERROR dispatch: atrybut ma pierwszeństwo przed nagłówkiem (nagłówek był już raz zwalidowany)")
    void errorDispatch_attributeWinsOverHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        simulateErrorDispatch(request);
        request.addHeader(CORRELATION_ID_HEADER, "client-id-1");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "first-pass-id");
        AtomicReference<String> mdcInChain = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (_, _) -> mdcInChain.set(MDC.get(CORRELATION_ID)));

        assertThat(mdcInChain.get()).isEqualTo("first-pass-id");
    }

    /**
     * Tak, jak robi to kontener: typ dispatchu ERROR, URI strony błędu i atrybut
     * {@code jakarta.servlet.error.request_uri}. Ten ostatni jest kluczowy — po nim (a nie po
     * {@code DispatcherType}) {@code OncePerRequestFilter} rozpoznaje ERROR dispatch w {@code shouldNotFilterErrorDispatch}.
     */
    private static void simulateErrorDispatch(MockHttpServletRequest request) {
        String originalUri = request.getRequestURI();
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, originalUri == null ? "/api/v1/x" : originalUri);
        request.setRequestURI("/error");
    }

    private Result run(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(CORRELATION_ID_HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInChain = new AtomicReference<>();

        filter.doFilter(request, response, (_, _) -> mdcInChain.set(MDC.get(CORRELATION_ID)));

        assertThat(MDC.get(CORRELATION_ID)).as("MDC po filtrze").isNull();
        return new Result(mdcInChain.get(), response.getHeader(CORRELATION_ID_HEADER));
    }

    private static void assertReplacedWithUuid(Result result) {
        assertThat(result.mdcInChain()).isNotNull();
        assertThat(UUID.fromString(result.mdcInChain()).toString()).isEqualTo(result.mdcInChain());
        assertThat(result.responseHeader()).isEqualTo(result.mdcInChain());
    }

    private record Result(String mdcInChain, String responseHeader) {
    }
}
