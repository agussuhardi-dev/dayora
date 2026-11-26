package app.dayora.identity.adapter.in.web;

import app.dayora.identity.domain.Account;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class AccountPrincipal extends User {
    private final String credentialVersion;
    public AccountPrincipal(Account account) {
        super(account.id().toString(), account.passwordHash(), account.enabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
        this.credentialVersion = version(account.passwordHash());
    }
    // BCrypt's random salt changes this version on every password reset; no extra schema column.
    public static String version(String hash) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public String credentialVersion() { return credentialVersion; }
}
