package pl.m22.gamehive.common.exception;

public class ExpiredJwtTokenException extends RuntimeException {
    public ExpiredJwtTokenException() {
        super("Token has expired");
    }
}
