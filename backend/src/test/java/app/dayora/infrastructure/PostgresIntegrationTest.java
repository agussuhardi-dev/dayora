package app.dayora.infrastructure;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Requires the local Compose database. Test writes are rolled back. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DAYORA_POSTGRES_TEST", matches = "true")
class PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;

    @Test void migrationsAndAccountPersistenceWork() {
        assertTrue(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class) >= 4);
        var user = accounts.create("test-" + UUID.randomUUID(), "integration-password", "Asia/Jakarta", Account.Role.USER);
        assertEquals(user.id(), accounts.byUsername(user.username()).id());
        assertNotEquals("integration-password", user.passwordHash());
    }

    @Test void offlineSyncTablesRemoved() {
        for (String table : new String[]{"device", "sync_operation_receipt", "change_log", "sync_cursor_counter"})
            assertNull(jdbc.queryForObject("select to_regclass(?)", String.class, table));
        assertNotNull(jdbc.queryForObject("select to_regclass('account_event_counter')", String.class));
    }
}
