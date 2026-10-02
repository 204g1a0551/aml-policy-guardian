package com.bank.aml.containers;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class TestcontainersPostgresIntegrationTest {

    private static boolean isDockerReady = false;

    @BeforeAll
    static void checkDockerEnvironment() {
        try {
            isDockerReady = DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ignored) {
            isDockerReady = false;
        }
        Assumptions.assumeTrue(isDockerReady, "Docker daemon not reachable by Testcontainers socket; skipping isolated container test.");
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("aml_testcontainer_db")
        .withUsername("test_user")
        .withPassword("test_pass");

    @Test
    @DisplayName("Testcontainers: Starts isolated PostgreSQL container and executes queries where Docker is practical")
    void testIsolatedContainerLifecycle() throws Exception {
        Assumptions.assumeTrue(isDockerReady, "Docker not available");
        assertThat(postgres.isRunning()).isTrue();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE isolated_test (id SERIAL PRIMARY KEY, note VARCHAR(100))");
            statement.execute("INSERT INTO isolated_test (note) VALUES ('Testcontainers AML Verification')");

            ResultSet rs = statement.executeQuery("SELECT note FROM isolated_test WHERE id = 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("note")).isEqualTo("Testcontainers AML Verification");
        }
    }

    @Test
    @DisplayName("Testcontainers: Validates database connection properties dynamically")
    void testContainerProperties() {
        Assumptions.assumeTrue(isDockerReady, "Docker not available");
        assertThat(postgres.getJdbcUrl()).contains("aml_testcontainer_db");
        assertThat(postgres.getUsername()).isEqualTo("test_user");
        assertThat(postgres.getPassword()).isEqualTo("test_pass");
        assertThat(postgres.getHost()).isNotBlank();
        assertThat(postgres.getFirstMappedPort()).isPositive();
    }
}
