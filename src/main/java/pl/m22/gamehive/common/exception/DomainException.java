package pl.m22.gamehive.common.exception;

public class DomainException extends BaseException {
    public DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
