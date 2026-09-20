package pl.m22.gamehive.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "DELETE")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException bez listy metod -> 405 bez Allow (getSupportedHttpMethods() jest @Nullable)")
    void methodNotSupportedWithoutSupportedMethods_omitsAllowHeader() {
        var response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isEmpty();   // bez strażnika setAllow(null) rzuciłoby NPE
    }

    @Test
    @DisplayName("NoResourceFoundException -> 404 RESOURCE_NOT_FOUND (nie ma takiej ścieżki, nie: nie ma takiej encji)")
    void noResourceFound_mapsTo404ResourceNotFound() {
        var response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/admin/taxonomy/nope", "No static resource"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("HttpMediaTypeNotAcceptableException -> 406 NOT_ACCEPTABLE z wymuszonym Content-Type: application/json")
    void notAcceptable_mapsTo406WithForcedJsonContentType() {
        var response = handler.handleNotAcceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));

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
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
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
}
