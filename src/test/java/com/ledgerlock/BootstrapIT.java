package com.ledgerlock;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class BootstrapIT extends AbstractPostgresIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired Environment environment;
  @Autowired TestRestTemplate http;

  @Test
  void applicationStartsAndServesHttpWithoutDomainEndpoints() {
    assertThat(entityManagerFactory.isOpen()).isTrue();
    // No controller exists in Milestone 0: an HTTP 404 proves the server responds.
    assertThat(http.getForEntity("/", String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void connectsToPinnedPostgresWithReadCommittedIsolation() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
      assertThat(connection.getTransactionIsolation())
          .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        try (var result = statement.executeQuery("SHOW server_version_num")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("170011");
        }
        try (var result = statement.executeQuery("SHOW transaction_isolation")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("read committed");
        }
        try (var result = statement.executeQuery("SHOW default_transaction_isolation")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("read committed");
        }
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void flywayInitializesHistoryBeforeDomainMigrationsExist() {
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(flyway.info().applied()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void persistenceConfigurationMatchesProjectRules() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(16);
  }
}
