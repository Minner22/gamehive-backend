package pl.m22.gamehive.common.exception;

public class InvalidJwtTypeException extends RuntimeException {
    public InvalidJwtTypeException() {
        super("Invalid JWT type");
    }

    public InvalidJwtTypeException(String type) {
        super("Invalid JWT type: " + type);
    }
}

