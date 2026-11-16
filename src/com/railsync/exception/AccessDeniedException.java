package com.railsync.exception;

/**
 * Thrown when a user tries to perform an operation their role is not permitted to do (e.g. a passenger calling an admin operation).
 */
public class AccessDeniedException extends RailSyncException {
    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }

}
