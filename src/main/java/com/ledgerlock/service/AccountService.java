package com.ledgerlock.service;

import com.ledgerlock.dto.AccountResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.AccountType;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer account operations; internal system accounts are not exposed through this service. */
@Service
public class AccountService {

  private final AccountRepository accountRepository;

  public AccountService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Transactional
  public AccountResponse create(String ownerName) {
    Account account = accountRepository.save(Account.customer(ownerName));
    return toResponse(account);
  }

  @Transactional(readOnly = true)
  public AccountResponse get(long id) {
    Account account =
        accountRepository
            .findByIdAndAccountType(id, AccountType.CUSTOMER)
            .orElseThrow(() -> new AccountNotFoundException(id));
    return toResponse(account);
  }

  private AccountResponse toResponse(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getOwnerName(),
        account.getBalanceSen(),
        account.getCreatedAt(),
        account.getUpdatedAt());
  }
}
