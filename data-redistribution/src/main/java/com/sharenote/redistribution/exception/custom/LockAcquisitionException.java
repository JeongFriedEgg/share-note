package com.sharenote.redistribution.exception.custom;

public class LockAcquisitionException extends DistributedLockException {
    public LockAcquisitionException(String message) {
        super(message);
    }
    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
