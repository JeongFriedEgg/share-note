package com.sharenote.redistribution.exception.custom;

public class DistributedLockException extends RuntimeException {
    public DistributedLockException(String message) {
        super(message);
    }
    public DistributedLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
