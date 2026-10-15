package com.forvmom.MomentForeverPayment.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified error response body (mirrors booking-service {@code ApiErrorResponse}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldError> fieldErrors;

    private ApiErrorResponse(ApiErrorResponseBuilder builder) {
        this.timestamp = LocalDateTime.now();
        this.status = builder.status;
        this.error = builder.error;
        this.code = builder.code;
        this.message = builder.message;
        this.path = builder.path;
        this.fieldErrors = builder.fieldErrors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public static ApiErrorResponseBuilder builder() {
        return new ApiErrorResponseBuilder();
    }

    public static class ApiErrorResponseBuilder {
        private int status;
        private String error;
        private String code;
        private String message;
        private String path;
        private List<FieldError> fieldErrors;

        public ApiErrorResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public ApiErrorResponseBuilder error(String error) {
            this.error = error;
            return this;
        }

        public ApiErrorResponseBuilder code(PaymentErrorCode code) {
            this.code = code.name();
            return this;
        }

        public ApiErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ApiErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ApiErrorResponseBuilder fieldErrors(List<FieldError> fieldErrors) {
            this.fieldErrors = fieldErrors;
            return this;
        }

        public ApiErrorResponse build() {
            return new ApiErrorResponse(this);
        }
    }
}
