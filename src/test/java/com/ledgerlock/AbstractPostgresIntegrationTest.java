package com.ledgerlock;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shares a temporary PostgreSQL database with the Spring test application.
 *
 * <p>Spring keeps the database alive while tests reuse the application, so a later test does not
 * inherit connections to a container that has already stopped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AbstractPostgresIntegrationTest.DatabaseConfiguration.class)
public abstract class AbstractPostgresIntegrationTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class DatabaseConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.11"))
          .withDatabaseName("ledgerlock_test")
          .withUsername("ledgerlock_test")
          .withPassword("test_only");
    }
  }
}
