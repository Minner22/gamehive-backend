package pl.m22.gamehive.common.exception;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() {
        super("Invalid passwoprd provided");
    }
}
