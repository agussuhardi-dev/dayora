package app.dayora.identity.adapter.out.persistence;

import app.dayora.identity.application.AccountRepository;
import app.dayora.identity.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SpringAccountRepository implements AccountRepository {
    private final UserJpaRepository users;
    public Optional<Account> findById(UUID id) { return users.findById(id).map(this::domain); }
    public Optional<Account> findByNormalizedUsername(String name) { return users.findByNormalizedUsername(name).map(this::domain); }
    public boolean existsByNormalizedUsername(String name) { return users.existsByNormalizedUsername(name); }
    public Account save(Account a) {
        var entity = users.findById(a.id()).orElseGet(() -> new UserJpaEntity(a.id(),a.username(),a.normalizedUsername(),a.passwordHash(),a.role().name(),a.timezone()));
        if (users.existsById(a.id())) { entity.setPasswordHash(a.passwordHash()); entity.setRole(a.role().name()); entity.setStatus(a.enabled()?"ACTIVE":"DISABLED"); entity.setTimezone(a.timezone()); entity.setUpdatedAt(a.updatedAt()); }
        return domain(users.save(entity));
    }
    private Account domain(UserJpaEntity u) { return new Account(u.getId(),u.getUsername(),u.getNormalizedUsername(),u.getPasswordHash(),Account.Role.valueOf(u.getRole()),"ACTIVE".equals(u.getStatus()),u.getTimezone(),u.getCreatedAt(),u.getUpdatedAt()); }
}
