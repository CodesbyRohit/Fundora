package com.fundora.controller;

import com.fundora.exception.LedgerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Module II & VI: Translates domain exceptions into meaningful HTTP responses
 * for the REST API instead of generic 500 errors with leaked stack traces.
 */
@RestControllerAdvice(assignableTypes = FundoraRestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<Map<String, String>> handleLedgerException(LedgerException ex) {
        return error(statusFor(ex.getErrorCode()), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        DateTimeParseException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        String message = (ex instanceof HttpMessageNotReadableException)
                ? "Malformed JSON request body."
                : ex.getMessage();
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        // Framework errors (405, 415, 404 ...) already carry the right status
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return error(HttpStatus.valueOf(status.value()), "HTTP_" + status.value(), ex.getMessage());
        }
        log.error("Unexpected API error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    static HttpStatus statusFor(String errorCode) {
        if (errorCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        switch (errorCode) {
            case LedgerException.NOT_FOUND:
            case "USER_NOT_FOUND":
                return HttpStatus.NOT_FOUND;
            case LedgerException.FORBIDDEN:
                return HttpStatus.FORBIDDEN;
            case "INSUFFICIENT_BALANCE":
                return HttpStatus.UNPROCESSABLE_ENTITY;
            case "LEDGER_ERR_CAUSE":
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
