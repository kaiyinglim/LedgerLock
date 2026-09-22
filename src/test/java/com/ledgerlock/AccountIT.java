package com.ledgerlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class AccountIT extends AbstractPostgresIntegrationTest {

  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clearAccounts() {
    // HTTP requests commit on server threads, outside any transaction on the test thread.
    jdbc.update("DELETE FROM accounts");
  }

  @Test
  void createsPersistsAndRetrievesZeroBalanceCustomer() {
    ResponseEntity<JsonNode> created = post("{\"ownerName\":\"  Kai  \"}");

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode account = created.getBody();
    assertThat(account).isNotNull();
    long id = account.get("id").asLong();
    assertThat(id).isPositive();
    assertThat(created.getHeaders().getLocation()).hasToString("/accounts/" + id);
    assertThat(account.get("ownerName").asText()).isEqualTo("Kai");
    assertThat(account.get("balanceSen").asLong()).isZero();
    assertThat(account.has("accountType")).isFalse();
    assertThat(Instant.parse(account.get("createdAt").asText())).isNotNull();
    assertThat(Instant.parse(account.get("updatedAt").asText())).isNotNull();
    assertThat(
            jdbc.queryForObject("SELECT account_type FROM accounts WHERE id = ?", String.class, id))
        .isEqualTo("CUSTOMER");
    assertThat(jdbc.queryForObject("SELECT balance_sen FROM accounts WHERE id = ?", Long.class, id))
        .isZero();

    ResponseEntity<JsonNode> fetched = http.getForEntity("/accounts/" + id, JsonNode.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isEqualTo(account);
  }

  @Test
  void acceptsMaximumLengthNameAndDoesNotRequireUniqueOwnerNames() {
    String request = "{\"ownerName\":\"" + "K".repeat(100) + "\"}";
    ResponseEntity<JsonNode> first = post(request);
    ResponseEntity<JsonNode> second = post(request);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(first.getBody().get("id")).isNotEqualTo(second.getBody().get("id"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"ownerName\":null}",
        "{\"ownerName\":\"\"}",
        "{\"ownerName\":\"   \"}",
        "{\"ownerName\":\"\\t\\n\"}",
        "null",
        "[]",
        "{broken",
        "{\"ownerName\":\"Kai\",\"openingBalanceSen\":10000}",
        "{\"ownerName\":\"Kai\",\"balanceSen\":10000}",
        "{\"ownerName\":\"Kai\",\"accountType\":\"SYSTEM\"}",
        "{\"ownerName\":\"Kai\",\"id\":7}"
      })
  void rejectsInvalidRequestsWithoutSaving(String body) {
    ResponseEntity<JsonNode> response = post(body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("status").asInt()).isEqualTo(400);
    assertThat(response.getBody().get("detail").asText()).isNotBlank();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isZero();
  }

  @Test
  void rejectsNameLongerThanLimit() {
    rejectsInvalidRequestsWithoutSaving("{\"ownerName\":\"" + "K".repeat(101) + "\"}");
  }

  @Test
  void returnsNotFoundForMissingAndSystemAccounts() {
    assertThat(http.getForEntity("/accounts/9223372036854775807", JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    // A test fixture checks the reserved account type; production seeds it in Milestone 2.
    Long systemId =
        jdbc.queryForObject(
            "INSERT INTO accounts (owner_name, account_type, balance_sen) "
                + "VALUES ('System fixture', 'SYSTEM', -5000) RETURNING id",
            Long.class);
    ResponseEntity<JsonNode> response = http.getForEntity("/accounts/" + systemId, JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("status").asInt()).isEqualTo(404);
    assertThat(response.getBody().get("detail").asText()).contains("was not found");
  }

  @Test
  void rejectsNonNumericId() {
    assertThat(http.getForEntity("/accounts/not-a-number", JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @CsvSource({
    "' ', CUSTOMER, 0, 23514", "Kai, INVALID, 0, 23514", "Kai, CUSTOMER, -1, 23514",
    "NULL, CUSTOMER, 0, 23502", "Kai, NULL, 0, 23502", "Kai, CUSTOMER, NULL, 23502"
  })
  void databaseRejectsInvalidRows(String name, String type, String balance, String sqlState) {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO accounts (owner_name, account_type, balance_sen) VALUES (?, ?, ?)",
                    "NULL".equals(name) ? null : name,
                    "NULL".equals(type) ? null : type,
                    "NULL".equals(balance) ? null : Long.valueOf(balance)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .satisfies(
            error -> {
              SQLException cause = (SQLException) error.getCause();
              assertThat(cause.getSQLState()).isEqualTo(sqlState);
            });
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class)).isZero();
  }

  @Test
  void databaseRejectsOverlongNames() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO accounts (owner_name, account_type) VALUES (?, 'CUSTOMER')",
                    "K".repeat(101)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsNegativeCustomerUpdate() {
    long id = post("{\"ownerName\":\"Kai\"}").getBody().get("id").asLong();
    assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance_sen = -1 WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("SELECT balance_sen FROM accounts WHERE id = ?", Long.class, id))
        .isZero();
  }

  @Test
  void schemaUsesBigintMoneyAndHasNoOptimisticVersionColumn() {
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'accounts' "
                    + "AND column_name = 'balance_sen'",
                String.class))
        .isEqualTo("bigint");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'accounts' "
                    + "AND column_name = 'version'",
                Integer.class))
        .isZero();
    Long id =
        jdbc.queryForObject(
            "INSERT INTO accounts (owner_name, account_type, balance_sen) "
                + "VALUES ('Large balance fixture', 'CUSTOMER', ?) RETURNING id",
            Long.class,
            Long.MAX_VALUE);
    assertThat(jdbc.queryForObject("SELECT balance_sen FROM accounts WHERE id = ?", Long.class, id))
        .isEqualTo(Long.MAX_VALUE);
  }

  private ResponseEntity<JsonNode> post(String json) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.postForEntity("/accounts", new HttpEntity<>(json, headers), JsonNode.class);
  }
}
