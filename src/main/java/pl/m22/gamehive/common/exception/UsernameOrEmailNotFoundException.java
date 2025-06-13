package pl.m22.gamehive.common.exception;

public class UsernameOrEmailNotFoundException extends RuntimeException {
    public UsernameOrEmailNotFoundException(String identifier) {
        super("Username or email not found: " + identifier);
    }
}
