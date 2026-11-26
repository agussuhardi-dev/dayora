package app.dayora.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, String username, String normalizedUsername, String passwordHash,
                      Role role, boolean enabled, String timezone, Instant createdAt, Instant updatedAt) {
    public enum Role { USER, ADMIN }
}
