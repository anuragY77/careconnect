package com.careconnect.common.error;

/**
 * One invalid field in a validation error. The rejected value is deliberately
 * not included, because it may be patient data.
 */
public record FieldViolation(String field, String message) {
}
