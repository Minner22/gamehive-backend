package pl.m22.gamehive.common.exception;

public class ExpiredActivationTokenException extends RuntimeException {
    public ExpiredActivationTokenException() {
        super("Activation token has expired");
    }
}
