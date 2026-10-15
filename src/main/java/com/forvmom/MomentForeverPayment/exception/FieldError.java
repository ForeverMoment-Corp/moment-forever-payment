package com.forvmom.MomentForeverPayment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Field-level validation detail (mirrors booking-service).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldError {

    private String field;
    private Object rejectedValue;
    private String message;

    public FieldError() {
    }

    private FieldError(Builder builder) {
        this.field = builder.field;
        this.rejectedValue = builder.rejectedValue;
        this.message = builder.message;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getField() {
        return field;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public String getMessage() {
        return message;
    }

    public static class Builder {
        private String field;
        private Object rejectedValue;
        private String message;

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder rejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public FieldError build() {
            return new FieldError(this);
        }
    }
}
