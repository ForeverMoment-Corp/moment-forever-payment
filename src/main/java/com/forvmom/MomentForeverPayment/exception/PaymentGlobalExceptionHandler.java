package com.forvmom.MomentForeverPayment.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for the payment service.
 * Mirrors booking-service {@code BookingGlobalExceptionHandler} so both
 * services return the same error shape.
 */
@RestControllerAdvice
public class PaymentGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentGlobalExceptionHandler.class);

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found — identifier={}, path={}", ex.getIdentifier(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, PaymentErrorCode.PAYMENT_NOT_FOUND,
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors().stream()
                .map(fe -> FieldError.builder()
                        .field(fe.getField())
                        .rejectedValue(fe.getRejectedValue())
                        .message(fe.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());
        log.warn("Validation failed — path={}, errors={}", request.getRequestURI(), fieldErrors.size());
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("BAD_REQUEST")
                .code(PaymentErrorCode.VALIDATION_FAILED)
                .message("Request validation failed. Check 'fieldErrors' for details.")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = String.format("Required query parameter '%s' of type '%s' is missing.",
                ex.getParameterName(), ex.getParameterType());
        return buildResponse(HttpStatus.BAD_REQUEST, PaymentErrorCode.INVALID_REQUEST,
                message, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Parameter '%s' must be of type '%s'. Received: '%s'.",
                ex.getName(), expectedType, ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, PaymentErrorCode.INVALID_REQUEST,
                message, request.getRequestURI());
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            RuntimeException ex, HttpServletRequest request) {
        log.warn("Illegal argument — path={}, message={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, PaymentErrorCode.INVALID_REQUEST,
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception — path={}, error={}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, PaymentErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later or contact support.",
                request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, PaymentErrorCode code, String message, String path) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase().toUpperCase().replace(" ", "_"))
                .code(code)
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
