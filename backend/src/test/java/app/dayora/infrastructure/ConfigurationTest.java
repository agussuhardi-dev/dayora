package app.dayora.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ConfigurationTest {
  @Test
  void applicationConfigUsesUtcLiquibaseAndSchemaValidation() throws Exception {
    var properties = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).getFirst();
    assertEquals("UTC", properties.getProperty("spring.jackson.time-zone"));
    assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("classpath:db/changelog/db.changelog-master.yaml",
        properties.getProperty("spring.liquibase.change-log"));
  }
}
