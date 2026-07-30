package pl.m22.gamehive.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

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
    @DisplayName("HttpMessageNotReadableException -> 400 VALIDATION_ERROR (puste / niepoprawne ciało żądania)")
    void unreadableBody_mapsTo400ValidationError() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("Required request body is missing", new MockHttpInputMessage(new byte[0])));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
    }
}
