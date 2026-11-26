package app.dayora.identity.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String username;
    @Column(name = "normalized_username", nullable = false, length = 120) private String normalizedUsername;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, length = 64) private String timezone;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public void setPasswordHash(String value) { this.passwordHash = value; }
    void setRole(String value) { this.role = value; }
    void setStatus(String value) { this.status = value; }
    void setTimezone(String value) { this.timezone = value; }
    void setUpdatedAt(Instant value) { this.updatedAt = value; }

    public UserJpaEntity(UUID id, String username, String normalizedUsername, String passwordHash, String role, String timezone) {
        this.id = id;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = "ACTIVE";
        this.timezone = timezone;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
