package pl.m22.gamehive.common.exception;

import java.util.List;

public record ApiValidationError(String errorCode, String message, List<FieldValidationError> errors) {
}
