package com.ledgerlock.entity;

import com.ledgerlock.exception.BalanceLimitExceededException;
import com.ledgerlock.exception.InvalidDepositAmountException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stores an account's current balance in integer sen.
 *
 * <p>Customer creation cannot supply a balance: funds enter through a recorded deposit. Optimistic
 * versioning is deliberately absent so the later lost-update demo is not hidden by automatic
 * version checks.
 */
@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_name", nullable = false, length = 100)
  private String ownerName;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 16)
  private AccountType accountType;

  @Column(name = "balance_sen", nullable = false)
  private long balanceSen;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Account() {}

  public static Account customer(String ownerName) {
    if (ownerName == null || ownerName.isBlank() || ownerName.strip().length() > 100) {
      throw new IllegalArgumentException("Owner name must contain between 1 and 100 characters");
    }
    Account account = new Account();
    account.ownerName = ownerName.strip();
    account.accountType = AccountType.CUSTOMER;
    account.balanceSen = 0;
    return account;
  }

  public Long getId() {
    return id;
  }

  /** Updates the cache; the caller must record the deposit in the same database transaction. */
  public void creditDeposit(long amountSen) {
    if (amountSen <= 0) {
      throw new InvalidDepositAmountException();
    }
    if (accountType != AccountType.CUSTOMER) {
      throw new IllegalStateException("Only customer accounts can receive deposits");
    }
    try {
      balanceSen = Math.addExact(balanceSen, amountSen);
    } catch (ArithmeticException exception) {
      throw new BalanceLimitExceededException();
    }
  }

  /**
   * Clearing represents external funds, so its balance decreases when a customer receives money.
   */
  public void debitClearingForDeposit(long amountSen) {
    if (amountSen <= 0) {
      throw new InvalidDepositAmountException();
    }
    if (accountType != AccountType.SYSTEM) {
      throw new IllegalStateException("Only the system account can clear deposits");
    }
    try {
      balanceSen = Math.subtractExact(balanceSen, amountSen);
    } catch (ArithmeticException exception) {
      throw new BalanceLimitExceededException();
    }
  }

  public String getOwnerName() {
    return ownerName;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public long getBalanceSen() {
    return balanceSen;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
