package app.dayora.identity.application;

import app.dayora.identity.domain.Account;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Optional<Account> findById(UUID id);
    Optional<Account> findByNormalizedUsername(String username);
    boolean existsByNormalizedUsername(String username);
    Account save(Account account);
}
