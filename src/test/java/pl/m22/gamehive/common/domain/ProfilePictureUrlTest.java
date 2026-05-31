package pl.m22.gamehive.common.domain;

import org.junit.jupiter.api.Test;
import pl.m22.gamehive.common.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.INVALID_PROFILE_PICTURE_URL;

class ProfilePictureUrlTest {

    @Test
    void accepts_https_url() {
        assertThat(new ProfilePictureUrl("https://x.com/a.jpg").value())
                .isEqualTo("https://x.com/a.jpg");
    }

    @Test
    void accepts_https_url_without_extension() {
        assertThat(new ProfilePictureUrl("https://cdn.example.com/avatar?id=123").value())
                .isEqualTo("https://cdn.example.com/avatar?id=123");
    }

    @Test
    void rejects_http_scheme() {
        assertThatThrownBy(() -> new ProfilePictureUrl("http://x.com/a.jpg"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_file_scheme() {
        assertThatThrownBy(() -> new ProfilePictureUrl("file:///etc/passwd"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_javascript_scheme() {
        assertThatThrownBy(() -> new ProfilePictureUrl("javascript:alert(1)"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new ProfilePictureUrl(null))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new ProfilePictureUrl("   "))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_garbage() {
        assertThatThrownBy(() -> new ProfilePictureUrl("not a url"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void rejects_https_without_host() {
        assertThatThrownBy(() -> new ProfilePictureUrl("https:/oops"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PROFILE_PICTURE_URL);
    }

    @Test
    void equal_values_are_equal() {
        assertThat(new ProfilePictureUrl("https://x.com/a.jpg"))
                .isEqualTo(new ProfilePictureUrl("https://x.com/a.jpg"))
                .hasSameHashCodeAs(new ProfilePictureUrl("https://x.com/a.jpg"));
    }

    @Test
    void toString_returns_raw_value() {
        assertThat(new ProfilePictureUrl("https://x.com/a.jpg")).hasToString("https://x.com/a.jpg");
    }
}
