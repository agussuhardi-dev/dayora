package app.dayora.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LiquibaseChangelogTest {
  @Test
  void masterIncludesAllChangelogFilesInOrder() throws IOException {
    var master = resource("db/changelog/db.changelog-master.yaml");
    assertTrue(master.indexOf("013-focus-workspaces.yaml") < master.indexOf("014-remove-offline-sync.yaml"));
    assertTrue(master.contains("databaseChangeLog:"));
  }

  @Test
  void changelogsContainExpectedInfrastructureTables() throws IOException {
    var initial = resource("db/changelog/changes/001-initial-schema.yaml");
    var sync = resource("db/changelog/changes/002-sync-schema.yaml");
    Stream.of("app_user", "task", "device").forEach(table -> assertTrue(initial.contains("tableName: " + table)));
    Stream.of("sync_operation_receipt", "change_log", "sync_cursor_counter")
        .forEach(table -> assertTrue(sync.contains("tableName: " + table)));
  }

  private static String resource(String name) throws IOException {
    try (var input = LiquibaseChangelogTest.class.getClassLoader().getResourceAsStream(name)) {
      if (input == null) throw new IOException("Missing resource: " + name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
