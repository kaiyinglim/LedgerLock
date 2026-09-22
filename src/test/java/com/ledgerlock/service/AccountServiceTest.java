package com.ledgerlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerlock.dto.AccountResponse;
import com.ledgerlock.entity.Account;
import com.ledgerlock.entity.AccountType;
import com.ledgerlock.exception.AccountNotFoundException;
import com.ledgerlock.repository.AccountRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

  @Mock AccountRepository repository;
  private AccountService service;

  @BeforeEach
  void setUp() {
    service = new AccountService(repository);
  }

  @Test
  void createsOnlyZeroBalanceCustomersAndReturnsSavedValues() {
    Account saved = storedAccount();
    when(repository.save(any(Account.class))).thenReturn(saved);

    AccountResponse response = service.create("  Kai  ");

    ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getOwnerName()).isEqualTo("Kai");
    assertThat(captor.getValue().getAccountType()).isEqualTo(AccountType.CUSTOMER);
    assertThat(captor.getValue().getBalanceSen()).isZero();
    assertThat(captor.getValue().getId()).isNull();
    assertThat(response.id()).isEqualTo(42);
    assertThat(response.ownerName()).isEqualTo("Kai");
    assertThat(response.balanceSen()).isZero();
    assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(response.updatedAt()).isEqualTo(response.createdAt());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void rejectsBlankNamesBeforeSaving(String name) {
    assertThatThrownBy(() -> service.create(name)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void rejectsLongNamesBeforeSaving() {
    assertThatThrownBy(() -> service.create("K".repeat(101)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void returnsCustomerAccount() {
    Account account = storedAccount();
    when(repository.findByIdAndAccountType(42, AccountType.CUSTOMER))
        .thenReturn(Optional.of(account));

    AccountResponse response = service.get(42);

    assertThat(response.id()).isEqualTo(42);
    assertThat(response.ownerName()).isEqualTo("Kai");
    assertThat(response.balanceSen()).isZero();
  }

  @Test
  void reportsMissingCustomerAccount() {
    when(repository.findByIdAndAccountType(42, AccountType.CUSTOMER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(42))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessage("Customer account 42 was not found");
  }

  private Account storedAccount() {
    Account account = mock(Account.class);
    when(account.getId()).thenReturn(42L);
    when(account.getOwnerName()).thenReturn("Kai");
    when(account.getBalanceSen()).thenReturn(0L);
    when(account.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    when(account.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    return account;
  }
}
