package Fuzzcode.model;

public enum Position {
    HOME,
    IN_TRANSIT_OUT,
    DELIVERED,
    IN_TRANSIT_RETURN;

    // Optional helper for mapping from DB string
    public static Position fromString(String s) {
        if (s == null) return null;
        try {
            return Position.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown position: " + s);
        }
    }
}