package pl.m22.gamehive.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/admin/taxonomy/publishers/1");

    @Test
    @DisplayName("DataIntegrityViolationException -> 409 DATA_CONFLICT (wyścig find-or-create / FK RESTRICT)")
    void dataIntegrityViolation_mapsTo409DataConflict() {
        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("DATA_CONFLICT");
    }

    @Test
    @DisplayName("PropertyReferenceException -> 400 VALIDATION_ERROR (nieznane pole w ?sort=, nie awaria serwera)")
    void unknownSortProperty_mapsTo400ValidationError() {
        var response = handler.handleUnknownSortProperty(
                new PropertyReferenceException("nosuchfield", TypeInformation.of(Object.class), List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException -> 405 METHOD_NOT_ALLOWED + nagłówek Allow")
    void methodNotSupported_mapsTo405WithAllowHeader() {
        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "DELETE")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException bez listy metod -> 405 i ZERO nagłówka Allow, nie pusty")
    void methodNotSupportedWithoutSupportedMethods_omitsAllowHeader() {
        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().containsHeader(HttpHeaders.ALLOW)).isFalse();
    }

    @Test
    @DisplayName("NoResourceFoundException -> 404 RESOURCE_NOT_FOUND (nie ma takiej ścieżki, nie: nie ma takiej encji)")
    void noResourceFound_mapsTo404ResourceNotFound() {
        var response = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/admin/taxonomy/nope", "No static resource"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("NoHandlerFoundException -> 404 RESOURCE_NOT_FOUND (ten sam handler; leci po wyłączeniu add-mappings)")
    void noHandlerFound_mapsTo404ResourceNotFound() {
        var response = handler.handleNotFound(
                new NoHandlerFoundException("GET", "/api/v1/admin/taxonomy/nope", new HttpHeaders()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("HttpMediaTypeNotAcceptableException -> 406 NOT_ACCEPTABLE z wymuszonym Content-Type: application/json")
    void notAcceptable_mapsTo406WithForcedJsonContentType() {
        var response = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("NOT_ACCEPTABLE");
        // bez jawnego Content-Type zapis ciała rozbiłby się o negocjację treści i poleciałby na /error
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException -> 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaType_mapsTo415() {
        var response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException -> 400 VALIDATION_ERROR")
    void missingRequestParameter_mapsTo400ValidationError() {
        // to, że Spring skieruje tę podklasę do handlera na typie nadrzędnym, rozstrzyga wyłącznie
        // RoutingErrorMappingTest.missingRequiredRequestParam_returns400 — tu handler wybiera wołający
        var response = handler.handleRequestBinding(
                new MissingServletRequestParameterException("token", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("MissingPathVariableException -> 500 INTERNAL_ERROR: to defekt mapowania po naszej stronie, nie błąd klienta")
    void missingPathVariable_mapsTo500NotClientError() {
        var response = handler.handleMissingPathVariable(new MissingPathVariableException("id", STUB_PARAMETER));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Odpowiedź błędu spoza 406 też ma wymuszony Content-Type: application/json (wspólny errorBuilder)")
    void everyErrorResponse_pinsJsonContentType() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("boom", new MockHttpInputMessage(new byte[0])));

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("HttpMessageNotReadableException -> 400 VALIDATION_ERROR (puste / niepoprawne ciało żądania)")
    void unreadableBody_mapsTo400ValidationError() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("Required request body is missing", new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    private static final MethodParameter STUB_PARAMETER = new MethodParameter(
            Objects.requireNonNull(ReflectionUtils.findMethod(GlobalExceptionHandlerTest.class, "stubHandler", String.class)), 0);

    @SuppressWarnings("unused")
    private void stubHandler(String id) {

    }
}
