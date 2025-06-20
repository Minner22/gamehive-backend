package pl.m22.gamehive.common.exception;

public class InvalidJwtSubjectException extends RuntimeException {
    public InvalidJwtSubjectException() {
        super("JWT does not contain a valid subject.");
    }
}
