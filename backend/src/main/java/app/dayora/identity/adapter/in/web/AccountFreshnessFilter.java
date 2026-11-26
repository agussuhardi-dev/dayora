package app.dayora.identity.adapter.in.web;

import app.dayora.identity.application.AccountService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
public class AccountFreshnessFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal principal) {
            try {
                var current = accounts.byId(UUID.fromString(principal.getUsername()));
                if (!current.enabled() || !AccountPrincipal.version(current.passwordHash()).equals(principal.credentialVersion())) {
                    SecurityContextHolder.clearContext(); var session = req.getSession(false); if (session != null) session.invalidate(); res.sendError(401); return;
                }
            } catch (RuntimeException e) { SecurityContextHolder.clearContext(); var session = req.getSession(false); if (session != null) session.invalidate(); res.sendError(401); return; }
        }
        chain.doFilter(req,res);
    }
}
