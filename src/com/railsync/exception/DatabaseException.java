package com.railsync.exception;

/**
 * Unchecked wrapper for {@link java.sql.SQLException}. Repository methods that implement the
 * generic {@code Repository<T, ID>} contract cannot declare checked SQL exceptions, so they
 * wrap them in this type and keep the original exception as the cause.
 */
public class DatabaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(String message) {
        super(message);
    }
}
