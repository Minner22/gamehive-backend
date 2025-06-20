package pl.m22.gamehive.common.exception;

public class InvalidJwtJtiException extends RuntimeException {
    public InvalidJwtJtiException(String string) {
        super(string);
    }
}
