package Fuzzcode.Server.model;

import java.time.Instant;

public record AppUser(
        int userId,
        String username,
        String passwordHash,
        String salt,
        UserRole role,
        Instant createdAt
) {
    @Override
    public String toString() {
        return "AppUser {" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", createdAt=" + createdAt +
                '}';
    }
}
