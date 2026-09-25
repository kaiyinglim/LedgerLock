package com.ledgerlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerlock.dto.DepositResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.AccountType;
import com.ledgerlock.entity.LedgerTransaction;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.exception.InvalidDepositAmountException;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.service.ledger.LedgerWriter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

  @Mock AccountRepository accounts;
  @Mock LedgerWriter ledgerWriter;
  private DepositService deposits;

  @BeforeEach
  void setUp() {
    deposits = new DepositService(accounts, ledgerWriter);
  }

  @Test
  void changesBothBalancesAndRecordsTheSameAmount() {
    Account customer = Account.customer("Kai");
    ReflectionTestUtils.setField(customer, "id", 7L);
    Account clearing = Account.customer("Clearing fixture");
    ReflectionTestUtils.setField(clearing, "accountType", AccountType.SYSTEM);
    when(accounts.findByIdAndAccountType(7, AccountType.CUSTOMER))
        .thenReturn(Optional.of(customer));
    when(accounts.findByAccountType(AccountType.SYSTEM)).thenReturn(Optional.of(clearing));
    when(ledgerWriter.recordDeposit(any(), any(), anyLong()))
        .thenAnswer(
            invocation -> {
              LedgerTransaction transaction =
                  LedgerTransaction.deposit(
                      invocation.getArgument(0),
                      invocation.getArgument(1),
                      invocation.getArgument(2));
              ReflectionTestUtils.setField(transaction, "id", 11L);
              ReflectionTestUtils.setField(
                  transaction, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
              return transaction;
            });

    DepositResponse response = deposits.deposit(7, 5000);

    assertThat(customer.getBalanceSen()).isEqualTo(5000);
    assertThat(clearing.getBalanceSen()).isEqualTo(-5000);
    verify(ledgerWriter).recordDeposit(clearing, customer, 5000);
    assertThat(response)
        .isEqualTo(new DepositResponse(11, 7, 5000, 5000, Instant.parse("2026-01-01T00:00:00Z")));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, Long.MIN_VALUE})
  void rejectsInvalidAmountsBeforeAccessingDatabase(long amount) {
    assertThatThrownBy(() -> deposits.deposit(7, amount))
        .isInstanceOf(InvalidDepositAmountException.class);
    verifyNoInteractions(accounts, ledgerWriter);
  }

  @Test
  void rejectsMissingCustomerWithoutWritingLedger() {
    when(accounts.findByIdAndAccountType(7, AccountType.CUSTOMER)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> deposits.deposit(7, 5000))
        .isInstanceOf(AccountNotFoundException.class);
    verifyNoInteractions(ledgerWriter);
  }

  @Test
  void missingClearingAccountDoesNotChangeCustomer() {
    Account customer = Account.customer("Kai");
    when(accounts.findByIdAndAccountType(7, AccountType.CUSTOMER))
        .thenReturn(Optional.of(customer));
    when(accounts.findByAccountType(AccountType.SYSTEM)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> deposits.deposit(7, 5000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("System clearing account is missing");
    assertThat(customer.getBalanceSen()).isZero();
    verifyNoInteractions(ledgerWriter);
  }
}
