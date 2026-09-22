package com.ledgerlock.entity;

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
 * <p>Customer creation cannot supply a balance: funds must enter through a recorded deposit in a
 * later milestone. Optimistic versioning is deliberately absent so the later lost-update demo is
 * not hidden by automatic version checks.
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
