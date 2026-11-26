package app.dayora.identity.application;

import app.dayora.identity.adapter.out.persistence.UserJpaEntity;
import app.dayora.identity.adapter.out.persistence.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BootstrapAdmin implements ApplicationRunner {
    private final UserJpaRepository users;
    private final PasswordEncoder passwords;
    @Value("${DAYORA_BOOTSTRAP_ADMIN_USERNAME:}") private String username;
    @Value("${DAYORA_BOOTSTRAP_ADMIN_PASSWORD:}") private String password;
    @Value("${DAYORA_BOOTSTRAP_ADMIN_TIMEZONE:UTC}") private String timezone;

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        if (username.isBlank() || username.strip().length() > 120 || password.getBytes(StandardCharsets.UTF_8).length < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalStateException("Bootstrap admin requires a username and a 12-72 byte password");
        var normalized = username.strip().toLowerCase(Locale.ROOT);
        if (users.existsByNormalizedUsername(normalized)) return;
        ZoneId.of(timezone);
        users.save(new UserJpaEntity(UUID.randomUUID(), username.strip(), normalized, passwords.encode(password), "ADMIN", timezone));
    }
}
