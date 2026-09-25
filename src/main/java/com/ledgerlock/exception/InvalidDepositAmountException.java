package com.ledgerlock.exception;

public class InvalidDepositAmountException extends RuntimeException {

  public InvalidDepositAmountException() {
    super("amountSen must be a positive whole number");
  }
}
