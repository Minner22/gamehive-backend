package pl.m22.gamehive.common.exception;

public class JwtPrivateKeyLengthException extends RuntimeException {

    public JwtPrivateKeyLengthException(String message) {
        super(message);
    }

}
