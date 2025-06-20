package pl.m22.gamehive.common.exception;

public class InvalidJwtRolesException extends RuntimeException {
    public InvalidJwtRolesException(String string) {
        super(string);
    }
}
