package pl.m22.gamehive.common.exception;

public class InvalidJwtAlgorithmException extends RuntimeException {
    public InvalidJwtAlgorithmException(String algorithm) {
        super("Invalid JWT algorithm: " + algorithm);
    }
}
