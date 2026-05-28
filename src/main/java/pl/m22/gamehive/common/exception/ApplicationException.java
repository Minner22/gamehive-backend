package pl.m22.gamehive.common.exception;

/**
 * Problem w przepływie use-case: nie znaleziono encji, niepoprawny stan tokena,
 * naruszenie warunku wstępnego operacji.
 *
 * <p>Loguje na poziomie WARN — godne uwagi, ale nie alarm.
 *
 * <p>Przykłady:
 * <ul>
 *   <li>{@link ErrorCode#USER_NOT_FOUND} — lookup miss</li>
 *   <li>{@link ErrorCode#JWT_EXPIRED} — token wygasł</li>
 *   <li>{@link ErrorCode#JWT_BLACKLISTED} — token już użyty</li>
 * </ul>
 */

public class ApplicationException extends BaseException {
    public ApplicationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    public ApplicationException(ErrorCode errorCode) {
        super(errorCode);
        }
}
