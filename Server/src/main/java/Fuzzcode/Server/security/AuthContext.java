package Fuzzcode.Server.security;

import java.time.Instant;
import java.util.Set;

public record AuthContext(
        String issuer,
        String subject,
        Set<String> scopes,
        String audience,
        Instant expiresAt
) {
    public boolean hasScope(String s) { return scopes.contains(s); }
}
