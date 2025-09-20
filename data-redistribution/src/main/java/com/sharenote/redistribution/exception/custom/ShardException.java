package com.sharenote.redistribution.exception.custom;

public class ShardException extends RuntimeException {
  public ShardException(String message) {
    super(message);
  }
  public ShardException(String message, Throwable cause) {
    super(message, cause);
  }
}
