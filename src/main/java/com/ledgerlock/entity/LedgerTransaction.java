package com.ledgerlock.entity;

import com.ledgerlock.exception.InvalidDepositAmountException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Groups the entries for one money movement, distinct from a database transaction.
 *
 * <p>The deposit factory builds an equal and opposite pair. Callers cannot append entries or edit
 * their amounts; the database's per-row checks alone cannot guarantee a balanced pair.
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, updatable = false, length = 16)
  private LedgerTransactionType transactionType;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "ledgerTransaction", cascade = CascadeType.PERSIST)
  private List<LedgerEntry> entries = new ArrayList<>();

  protected LedgerTransaction() {}

  public static LedgerTransaction deposit(Account clearing, Account customer, long amountSen) {
    if (amountSen <= 0) {
      throw new InvalidDepositAmountException();
    }
    if (clearing.getAccountType() != AccountType.SYSTEM
        || customer.getAccountType() != AccountType.CUSTOMER) {
      throw new IllegalArgumentException("A deposit must link clearing to a customer account");
    }
    LedgerTransaction transaction = new LedgerTransaction();
    transaction.transactionType = LedgerTransactionType.DEPOSIT;
    transaction.entries.add(new LedgerEntry(transaction, clearing, -amountSen));
    transaction.entries.add(new LedgerEntry(transaction, customer, amountSen));
    return transaction;
  }

  public Long getId() {
    return id;
  }

  public LedgerTransactionType getTransactionType() {
    return transactionType;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<LedgerEntry> getEntries() {
    return List.copyOf(entries);
  }
}
