package pl.m22.gamehive.common.exception;

public class InvalidJwtSignature extends RuntimeException {
    public InvalidJwtSignature() {
        super("Invalid JWT signature");
    }
}
