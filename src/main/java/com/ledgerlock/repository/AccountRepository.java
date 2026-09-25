package com.ledgerlock.repository;

import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.AccountType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

  Optional<Account> findByIdAndAccountType(long id, AccountType accountType);

  Optional<Account> findByAccountType(AccountType accountType);
}
