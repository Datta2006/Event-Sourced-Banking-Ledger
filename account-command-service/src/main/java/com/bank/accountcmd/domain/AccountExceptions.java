package com.bank.accountcmd.domain;

/** Domain exceptions mapped to HTTP status codes by the API exception handler. */
public final class AccountExceptions {

    private AccountExceptions() {
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }
}
