package app.dayora.identity.adapter.in.web;

import app.dayora.identity.application.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DayoraUserDetailsService implements UserDetailsService {
    private final AccountService accounts;
    @Override public UserDetails loadUserByUsername(String username) {
        try { return new AccountPrincipal(accounts.byUsername(username)); }
        catch (IllegalArgumentException e) { throw new UsernameNotFoundException("Invalid credentials"); }
    }
}
