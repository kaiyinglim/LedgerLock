package com.ledgerlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class DepositIT extends AbstractPostgresIntegrationTest {

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @Test
  void depositCreatesBalancedPairAndUpdatesAccountResponse() {
    long customerId = createCustomer();
    ResponseEntity<JsonNode> response = deposit(customerId, "{\"amountSen\":5000}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = response.getBody();
    assertThat(body.get("accountId").asLong()).isEqualTo(customerId);
    assertThat(body.get("amountSen").asLong()).isEqualTo(5000);
    assertThat(body.get("balanceSen").asLong()).isEqualTo(5000);
    assertThat(Instant.parse(body.get("createdAt").asText())).isNotNull();
    long transactionId = body.get("ledgerTransactionId").asLong();
    assertThat(transactionId).isPositive();
    assertThat(
            jdbc.queryForObject(
                "SELECT transaction_type FROM ledger_transactions WHERE id = ?",
                String.class,
                transactionId))
        .isEqualTo("DEPOSIT");
    assertThat(
            jdbc.queryForList(
                "SELECT amount_sen FROM ledger_entries WHERE ledger_transaction_id = ? ORDER BY"
                    + " amount_sen",
                Long.class,
                transactionId))
        .containsExactly(-5000L, 5000L);
    assertThat(
            jdbc.queryForObject(
                "SELECT amount_sen FROM ledger_entries WHERE ledger_transaction_id = ? AND"
                    + " account_id = ?",
                Long.class,
                transactionId,
                customerId))
        .isEqualTo(5000);
    assertThat(balance(clearingId())).isEqualTo(-5000);
    JsonNode account = http.getForObject("/accounts/" + customerId, JsonNode.class);
    assertThat(account.get("balanceSen").asLong()).isEqualTo(5000);
    assertLedgerInvariants();
  }

  @Test
  void repeatedDepositsAcrossCustomersReconcileIncludingEmptyAccounts() {
    long first = createCustomer();
    long second = createCustomer();
    createCustomer();
    assertThat(deposit(first, "{\"amountSen\":5000}").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(deposit(first, "{\"amountSen\":250}").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(deposit(second, "{\"amountSen\":1}").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(balance(first)).isEqualTo(5250);
    assertThat(balance(second)).isEqualTo(1);
    assertThat(balance(clearingId())).isEqualTo(-5251);
    assertThat(count("ledger_transactions")).isEqualTo(3);
    assertThat(count("ledger_entries")).isEqualTo(6);
    assertLedgerInvariants();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "null",
        "{broken",
        "{\"amountSen\":null}",
        "{\"amountSen\":0}",
        "{\"amountSen\":-1}",
        "{\"amountSen\":-9223372036854775808}",
        "{\"amountSen\":1.5}",
        "{\"amountSen\":1.0}",
        "{\"amountSen\":\"5000\"}",
        "{\"amountSen\":true}",
        "{\"amountSen\":9223372036854775808}",
        "{\"amountSen\":1,\"balanceSen\":5}"
      })
  void rejectsInvalidAmountsWithoutChangingAnything(String body) {
    long customerId = createCustomer();
    ResponseEntity<JsonNode> response = deposit(customerId, body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("status").asInt()).isEqualTo(400);
    assertThat(balance(customerId)).isZero();
    assertThat(balance(clearingId())).isZero();
    assertThat(count("ledger_transactions")).isZero();
    assertThat(count("ledger_entries")).isZero();
    assertLedgerInvariants();
  }

  @Test
  void rejectsMissingAndSystemAccountTargets() {
    assertThat(deposit(Long.MAX_VALUE, "{\"amountSen\":5000}").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(deposit(clearingId(), "{\"amountSen\":5000}").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(count("ledger_transactions")).isZero();
    assertThat(balance(clearingId())).isZero();
    assertLedgerInvariants();
  }

  @Test
  void rejectsCustomerOverflowWithoutAddingLedgerMovement() {
    long customer = createCustomer();
    assertThat(deposit(customer, "{\"amountSen\":9223372036854775807}").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    ResponseEntity<JsonNode> rejected = deposit(customer, "{\"amountSen\":1}");
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(rejected.getBody().get("detail").asText()).contains("balance range");
    assertThat(balance(customer)).isEqualTo(Long.MAX_VALUE);
    assertThat(balance(clearingId())).isEqualTo(-Long.MAX_VALUE);
    assertThat(count("ledger_transactions")).isEqualTo(1);
    assertLedgerInvariants();
  }

  @Test
  void clearingOverflowRollsBackEarlierCustomerMutation() {
    long first = createCustomer();
    long second = createCustomer();
    assertThat(deposit(first, "{\"amountSen\":9223372036854775807}").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(deposit(second, "{\"amountSen\":1}").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(balance(clearingId())).isEqualTo(Long.MIN_VALUE);
    assertThat(deposit(second, "{\"amountSen\":1}").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(balance(second)).isEqualTo(1);
    assertThat(balance(clearingId())).isEqualTo(Long.MIN_VALUE);
    assertThat(count("ledger_transactions")).isEqualTo(2);
    assertLedgerInvariants();
  }

  @Test
  void databaseAllowsOnlyOneSystemAccount() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO accounts (owner_name, account_type) VALUES ('Duplicate clearing',"
                        + " 'SYSTEM')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_type = 'SYSTEM'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void databaseRejectsZeroNullAndOrphanEntriesAndDuplicateAccountEntries() {
    long account = createCustomer();
    Long transaction =
        jdbc.queryForObject(
            "INSERT INTO ledger_transactions (transaction_type) VALUES ('DEPOSIT') RETURNING id",
            Long.class);
    assertThatThrownBy(() -> insertEntry(transaction, account, 0L))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertEntry(transaction, account, null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertEntry(transaction, Long.MAX_VALUE, 1L))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertEntry(Long.MAX_VALUE, account, 1L))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("ledger_entries")).isZero();
    insertEntry(transaction, account, 1L);
    assertThatThrownBy(() -> insertEntry(transaction, account, -1L))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM accounts WHERE id = ?", account))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM ledger_transactions WHERE id = ?", transaction))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsMissingOrUnknownMovementTypeAndUsesBigintEntries() {
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO ledger_transactions (transaction_type) VALUES (NULL)"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO ledger_transactions (transaction_type) VALUES ('UNKNOWN')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' "
                    + "AND table_name = 'ledger_entries' AND column_name = 'amount_sen'",
                String.class))
        .isEqualTo("bigint");
  }

  private void assertLedgerInvariants() {
    assertThat(
            jdbc.queryForList(
                "SELECT t.id FROM ledger_transactions t LEFT JOIN ledger_entries e "
                    + "ON e.ledger_transaction_id = t.id GROUP BY t.id "
                    + "HAVING COUNT(e.id) <> 2 OR COALESCE(SUM(e.amount_sen), 0) <> 0"))
        .as("Every movement has exactly two entries summing to zero")
        .isEmpty();
    assertThat(
            jdbc.queryForList(
                "SELECT a.id FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id "
                    + "GROUP BY a.id HAVING a.balance_sen <> COALESCE(SUM(e.amount_sen), 0)"))
        .as("Every cached balance reconciles, including clearing and empty accounts")
        .isEmpty();
    assertThat(jdbc.queryForObject("SELECT SUM(balance_sen) FROM accounts", BigDecimal.class))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM accounts WHERE account_type = 'CUSTOMER' AND balance_sen < 0"))
        .isEmpty();
  }

  private long createCustomer() {
    return post("/accounts", "{\"ownerName\":\"Kai\"}").getBody().get("id").asLong();
  }

  private long clearingId() {
    return jdbc.queryForObject("SELECT id FROM accounts WHERE account_type = 'SYSTEM'", Long.class);
  }

  private long balance(long id) {
    return jdbc.queryForObject("SELECT balance_sen FROM accounts WHERE id = ?", Long.class, id);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private void insertEntry(long transaction, long account, Long amount) {
    jdbc.update(
        "INSERT INTO ledger_entries (ledger_transaction_id, account_id, amount_sen) "
            + "VALUES (?, ?, ?)",
        transaction,
        account,
        amount);
  }

  private ResponseEntity<JsonNode> deposit(long account, String body) {
    return post("/accounts/" + account + "/deposits", body);
  }

  private ResponseEntity<JsonNode> post(String path, String json) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.postForEntity(path, new HttpEntity<>(json, headers), JsonNode.class);
  }
}
