package com.ledgerlock.service.ledger;

import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.LedgerTransaction;
import com.ledgerlock.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records ledger movements within the same database transaction as their balance changes. */
@Component
public class LedgerWriter {

  private final LedgerTransactionRepository transactions;

  public LedgerWriter(LedgerTransactionRepository transactions) {
    this.transactions = transactions;
  }

  // Refuse an independent write that could commit without its associated balance changes.
  @Transactional(propagation = Propagation.MANDATORY)
  public LedgerTransaction recordDeposit(Account clearing, Account customer, long amountSen) {
    return transactions.save(LedgerTransaction.deposit(clearing, customer, amountSen));
  }
}
