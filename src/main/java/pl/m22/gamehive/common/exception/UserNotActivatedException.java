package pl.m22.gamehive.common.exception;

public class UserNotActivatedException extends RuntimeException {
    public UserNotActivatedException(String email) {
        super("User not activated: " + email);
    }
}
