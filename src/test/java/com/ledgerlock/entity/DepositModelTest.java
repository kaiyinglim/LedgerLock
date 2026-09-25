package com.ledgerlock.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerlock.exception.BalanceLimitExceededException;
import com.ledgerlock.exception.InvalidDepositAmountException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class DepositModelTest {

  @Test
  void buildsEqualAndOppositeEntriesWithoutAllowingCallersToAppend() {
    Account customer = Account.customer("Kai");
    Account clearing = clearingAccount();
    LedgerTransaction transaction = LedgerTransaction.deposit(clearing, customer, 5000);

    assertThat(transaction.getTransactionType()).isEqualTo(LedgerTransactionType.DEPOSIT);
    assertThat(transaction.getEntries())
        .extracting(LedgerEntry::getAmountSen)
        .containsExactly(-5000L, 5000L);
    assertThat(transaction.getEntries())
        .extracting(LedgerEntry::getAccount)
        .containsExactly(clearing, customer);
    assertThatThrownBy(() -> transaction.getEntries().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, Long.MIN_VALUE})
  void rejectsNonpositiveAmountsWithoutChangingBalances(long amount) {
    Account customer = Account.customer("Kai");
    Account clearing = clearingAccount();
    assertThatThrownBy(() -> customer.creditDeposit(amount))
        .isInstanceOf(InvalidDepositAmountException.class);
    assertThatThrownBy(() -> clearing.debitClearingForDeposit(amount))
        .isInstanceOf(InvalidDepositAmountException.class);
    assertThatThrownBy(() -> LedgerTransaction.deposit(clearing, customer, amount))
        .isInstanceOf(InvalidDepositAmountException.class);
    assertThat(customer.getBalanceSen()).isZero();
    assertThat(clearing.getBalanceSen()).isZero();
  }

  @Test
  void guardsAccountRoles() {
    Account customer = Account.customer("Kai");
    Account clearing = clearingAccount();
    assertThatThrownBy(() -> clearing.creditDeposit(1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> customer.debitClearingForDeposit(1))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> LedgerTransaction.deposit(customer, clearing, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void detectsOverflowBeforeAssigningWrappedBalance() {
    Account customer = Account.customer("Kai");
    Account clearing = clearingAccount();
    customer.creditDeposit(Long.MAX_VALUE);
    clearing.debitClearingForDeposit(Long.MAX_VALUE);
    clearing.debitClearingForDeposit(1);
    assertThatThrownBy(() -> customer.creditDeposit(1))
        .isInstanceOf(BalanceLimitExceededException.class);
    assertThatThrownBy(() -> clearing.debitClearingForDeposit(1))
        .isInstanceOf(BalanceLimitExceededException.class);
    assertThat(customer.getBalanceSen()).isEqualTo(Long.MAX_VALUE);
    assertThat(clearing.getBalanceSen()).isEqualTo(Long.MIN_VALUE);
  }

  private Account clearingAccount() {
    // Production creates this role only through Flyway; unit fixtures need no database.
    Account account = Account.customer("Clearing fixture");
    ReflectionTestUtils.setField(account, "accountType", AccountType.SYSTEM);
    return account;
  }
}
