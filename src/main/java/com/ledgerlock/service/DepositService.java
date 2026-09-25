package com.ledgerlock.service;

import com.ledgerlock.dto.DepositResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.AccountType;
import com.ledgerlock.entity.LedgerTransaction;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.exception.InvalidDepositAmountException;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.service.ledger.LedgerWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService {

  private final AccountRepository accounts;
  private final LedgerWriter ledgerWriter;

  public DepositService(AccountRepository accounts, LedgerWriter ledgerWriter) {
    this.accounts = accounts;
    this.ledgerWriter = ledgerWriter;
  }

  /**
   * Commits both balance changes and the balanced ledger pair together.
   *
   * <p>This read-then-write implementation is for sequential use. The transaction provides
   * rollback, but no explicit locking yet protects cached balances from concurrent deposits.
   */
  @Transactional
  public DepositResponse deposit(long accountId, long amountSen) {
    if (amountSen <= 0) {
      throw new InvalidDepositAmountException();
    }
    Account customer =
        accounts
            .findByIdAndAccountType(accountId, AccountType.CUSTOMER)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    Account clearing =
        accounts
            .findByAccountType(AccountType.SYSTEM)
            .orElseThrow(() -> new IllegalStateException("System clearing account is missing"));

    customer.creditDeposit(amountSen);
    clearing.debitClearingForDeposit(amountSen);
    LedgerTransaction transaction = ledgerWriter.recordDeposit(clearing, customer, amountSen);
    return new DepositResponse(
        transaction.getId(),
        customer.getId(),
        amountSen,
        customer.getBalanceSen(),
        transaction.getCreatedAt());
  }
}
