package pl.m22.gamehive.common.domain;

import org.jspecify.annotations.NonNull;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;

public record ProfilePictureUrl(String value) {

    public ProfilePictureUrl {

        if (value == null || value.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_PROFILE_PICTURE_URL, "Profile picture URL must not be blank");
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new DomainException(ErrorCode.INVALID_PROFILE_PICTURE_URL, "Profile picture URL is not a valid URL");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new DomainException(ErrorCode.INVALID_PROFILE_PICTURE_URL, "Profile picture URL must use the http scheme");
        }

        // Świadomie NIE walidujemy rozszerzenia pliku: użytkownik może wkleić URL z CDN
        // bez rozszerzenia (np. https://cdn.example.com/avatar?id=123).
        // w przyszłości może się zmienić i będziemy ustawiali obrazki z własnych zasobów
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}
