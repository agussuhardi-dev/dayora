package app.dayora.identity.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** ponytail: single-process ceiling; use shared storage before multi-instance deployment. */
final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final String path;
    private final int maxAttempts;
    LoginRateLimitFilter() { this("/api/auth/login", 10); }
    LoginRateLimitFilter(String path, int maxAttempts) {
        this.path = path;
        this.maxAttempts = maxAttempts;
    }
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int MAX_KEYS = 4096;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected String getAlreadyFilteredAttributeName() {
        return getClass().getName() + path + ".FILTERED";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !request.getRequestURI().equals(request.getContextPath() + path)) {
            chain.doFilter(request, response);
            return;
        }
        if (!allow(request.getRemoteAddr(), System.nanoTime() / 1_000_000)) {
            response.setHeader("Retry-After", "60");
            response.setStatus(429);
            return;
        }
        chain.doFilter(request, response);
    }

    synchronized boolean allow(String key, long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        if (!windows.containsKey(key) && windows.size() >= MAX_KEYS) return false;
        var window = windows.computeIfAbsent(key, ignored -> new Window(now));
        return window.attempts.incrementAndGet() <= maxAttempts;
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger attempts = new AtomicInteger();
        private Window(long startedAt) { this.startedAt = startedAt; }
    }
}
