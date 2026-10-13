package com.fundora.exception;

/**
 * Module II (Inheritance & Exception Handling): Base application exception
 */
public class LedgerException extends RuntimeException {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String VALIDATION = "VALIDATION_ERROR";

    private final String errorCode;

    public LedgerException(String message) {
        super(message);
        this.errorCode = "LEDGER_ERR_GENERIC";
    }

    public LedgerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LedgerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "LEDGER_ERR_CAUSE";
    }

    public static LedgerException notFound(String message) {
        return new LedgerException(NOT_FOUND, message);
    }

    public static LedgerException forbidden(String message) {
        return new LedgerException(FORBIDDEN, message);
    }

    public static LedgerException invalid(String message) {
        return new LedgerException(VALIDATION, message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
