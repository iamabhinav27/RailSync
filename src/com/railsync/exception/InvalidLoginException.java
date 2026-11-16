package com.railsync.exception;

/**
 * Thrown when login or registration input is invalid or the credentials do not match.
 */
public class InvalidLoginException extends RailSyncException {
    private static final long serialVersionUID = 1L;

    public InvalidLoginException(String message) {
        super(message);
    }

}
