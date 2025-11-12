package Fuzzcode.model;

public enum UserRole {
    ADMIN, USER;

    public static UserRole fromDb(String s) {
        return s == null ? USER : UserRole.valueOf(s.toUpperCase());
    }
}
