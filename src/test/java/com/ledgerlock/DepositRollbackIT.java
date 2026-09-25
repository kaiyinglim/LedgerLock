package com.ledgerlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.ledgerlock.entity.Account;
import com.ledgerlock.repository.AccountRepository;
import com.ledgerlock.service.DepositService;
import com.ledgerlock.service.ledger.LedgerWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

class DepositRollbackIT extends AbstractPostgresIntegrationTest {

  @Autowired DepositService deposits;
  @Autowired AccountRepository accounts;
  @Autowired JdbcTemplate jdbc;
  @PersistenceContext EntityManager entityManager;
  @MockitoSpyBean LedgerWriter ledgerWriter;

  @Test
  void failureAfterFlushedWritesRollsBackBalancesAndLedgerButPreservesEarlierDeposit() {
    long customerId = accounts.save(Account.customer("Kai")).getId();
    deposits.deposit(customerId, 1000);

    // Configure the spy behind Spring's wrapper; setup itself has no database transaction.
    LedgerWriter writerTarget = AopTestUtils.getUltimateTargetObject(ledgerWriter);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              // Force balance and ledger SQL to run so this proves rollback of actual database
              // writes.
              entityManager.flush();
              assertThat(
                      jdbc.queryForObject(
                          "SELECT COUNT(*) FROM ledger_transactions", Integer.class))
                  .isEqualTo(2);
              assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class))
                  .isEqualTo(4);
              assertThat(
                      jdbc.queryForObject(
                          "SELECT balance_sen FROM accounts WHERE id = ?", Long.class, customerId))
                  .isEqualTo(6000);
              assertThat(
                      jdbc.queryForObject(
                          "SELECT balance_sen FROM accounts WHERE account_type = 'SYSTEM'",
                          Long.class))
                  .isEqualTo(-6000);
              throw new IllegalStateException("Injected failure after database writes");
            })
        .when(writerTarget)
        .recordDeposit(any(), any(), anyLong());

    assertThatThrownBy(() -> deposits.deposit(customerId, 5000))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Injected failure after database writes");

    // No test-wide transaction: these reads observe the state after the service rolled back.
    assertThat(
            jdbc.queryForObject(
                "SELECT balance_sen FROM accounts WHERE id = ?", Long.class, customerId))
        .isEqualTo(1000);
    assertThat(
            jdbc.queryForObject(
                "SELECT balance_sen FROM accounts WHERE account_type = 'SYSTEM'", Long.class))
        .isEqualTo(-1000);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT a.id FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id "
                    + "GROUP BY a.id HAVING a.balance_sen <> COALESCE(SUM(e.amount_sen), 0)"))
        .isEmpty();
  }

  @Test
  void ledgerWriterRefusesToRunOutsideAnExistingTransaction() {
    assertThatThrownBy(() -> ledgerWriter.recordDeposit(null, null, 5000))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions", Integer.class))
        .isZero();
  }
}
