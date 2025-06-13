package pl.m22.gamehive.common.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("username already exists: " + username);
    }
}
