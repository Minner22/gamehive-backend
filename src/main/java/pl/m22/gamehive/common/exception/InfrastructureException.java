package pl.m22.gamehive.common.exception;

/**
 * Awaria systemu zewnętrznego lub warstwy technicznej: Redis, SMTP, biblioteka
 * kryptograficzna, baza danych. Najczęściej rezultat opakowania wyjątku z biblioteki
 * trzeciej strony.
 *
 * <p>Loguje na poziomie ERROR ze stack trace'em — to incydent wymagający diagnozy.
 *
 * <p>Przykłady:
 * <ul>
 *   <li>{@link ErrorCode#EMAIL_SEND_FAILED} — błąd SMTP</li>
 *   <li>{@link ErrorCode#REDIS_UNAVAILABLE} — Redis nieosiągalny</li>
 *   <li>{@link ErrorCode#JWT_SIGNING_ERROR} — błąd biblioteki JOSE przy podpisywaniu</li>
 * </ul>
 */

public class InfrastructureException extends BaseException {
    public InfrastructureException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InfrastructureException(ErrorCode errorCode) {
        super(errorCode);
    }
}
