package pl.m22.gamehive.common.exception;

public class InfrastructureException extends BaseException {
    public InfrastructureException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
