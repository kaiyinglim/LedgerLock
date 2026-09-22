package com.ledgerlock.exception;

public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(long id) {
    super("Customer account " + id + " was not found");
  }
}
