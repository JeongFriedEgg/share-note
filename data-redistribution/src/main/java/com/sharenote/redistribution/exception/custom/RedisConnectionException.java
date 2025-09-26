package com.sharenote.redistribution.exception.custom;

public class RedisConnectionException extends DistributedLockException {
    public RedisConnectionException(String message) {
        super(message);
    }
    public RedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
