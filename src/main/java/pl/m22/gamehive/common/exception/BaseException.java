package pl.m22.gamehive.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {
    private  final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}
