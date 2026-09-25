package com.ledgerlock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** A signed balance change created as part of a complete ledger movement. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ledger_transaction_id", nullable = false, updatable = false)
  private LedgerTransaction ledgerTransaction;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private Account account;

  @Column(name = "amount_sen", nullable = false, updatable = false)
  private long amountSen;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LedgerEntry() {}

  LedgerEntry(LedgerTransaction ledgerTransaction, Account account, long amountSen) {
    this.ledgerTransaction = ledgerTransaction;
    this.account = account;
    this.amountSen = amountSen;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public long getAmountSen() {
    return amountSen;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
