package pl.m22.gamehive.common.exception;

public class ApplicationException extends BaseException {
    public ApplicationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
