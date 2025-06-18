package pl.m22.gamehive.common.exception;

public class UserAlreadyActivatedException extends RuntimeException {
    public UserAlreadyActivatedException(String email) {
        super("User with email " + email + " is already activated.");
    }
}
