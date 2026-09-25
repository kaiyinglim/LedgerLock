package com.ledgerlock.exception;

public class BalanceLimitExceededException extends RuntimeException {

  public BalanceLimitExceededException() {
    super("Deposit exceeds the supported balance range");
  }
}
