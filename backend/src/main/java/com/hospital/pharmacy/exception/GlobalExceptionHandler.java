package com.hospital.pharmacy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException e) {
        HttpStatus status = switch (e.getCode()) {
            case TRACE_CODE_DUPLICATED, BAD_REQUEST, IDEMPOTENT_CONFLICT, PRESCRIPTION_MISMATCH -> HttpStatus.BAD_REQUEST;
            case IDEMPOTENT_PROCESSING, STOCK_CONFLICT, INVALID_STATE_TRANSITION, IDEMPOTENT_REPLAY,
                    HIS_MAPPING_REQUIRED, HIS_REVISION_CONFLICT, HIS_RETURN_REQUIRED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(e.getCode().name(), e.getMessage(), e.getRequestId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError(ErrorCode.BAD_REQUEST.name(), e.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ErrorCode.INVALID_STATE_TRANSITION.name(), e.getMessage(), null));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("HIS_UNAUTHORIZED", e.getMessage(), null));
    }
}
