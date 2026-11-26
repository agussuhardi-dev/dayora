package app.dayora.identity.application;

import app.dayora.identity.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public Account byUsername(String username) { return accounts.findByNormalizedUsername(normalize(username)).orElseThrow(() -> new IllegalArgumentException("Invalid credentials")); }
    public static class UsernameConflict extends RuntimeException {}
    public Account byId(UUID id) { return accounts.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Account not found")); }

    @Transactional
    public Account create(String username, String password, String timezone, Account.Role role) {
        var name = username == null ? "" : username.strip(); var normalized = normalize(name);
        if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Invalid username");
        if (accounts.existsByNormalizedUsername(normalized)) throw new UsernameConflict();
        validatePassword(password); validateTimezone(timezone);
        var now = Instant.now();
        return accounts.save(new Account(UUID.randomUUID(), name, normalized, passwords.encode(password), role, true, timezone, now, now));
    }
    @Transactional public Account enabled(UUID id, boolean enabled) { var a = byId(id); return accounts.save(new Account(a.id(),a.username(),a.normalizedUsername(),a.passwordHash(),a.role(),enabled,a.timezone(),a.createdAt(),Instant.now())); }
    @Transactional public Account resetPassword(UUID id, String password) { validatePassword(password); var a = byId(id); return accounts.save(new Account(a.id(),a.username(),a.normalizedUsername(),passwords.encode(password),a.role(),a.enabled(),a.timezone(),a.createdAt(),Instant.now())); }
    public static String normalize(String username) { return username.strip().toLowerCase(Locale.ROOT); }
    private static void validatePassword(String password) { if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must be 12-72 UTF-8 bytes"); }
    private static void validateTimezone(String timezone) { try { ZoneId.of(timezone); } catch (Exception e) { throw new IllegalArgumentException("Invalid timezone"); } }
}
