package Fuzzcode.model;

public record Person(
        int personId,
        String name,
        PersonRole role,
        boolean deleted
) {
    @Override
    public String toString() {
        return "Person {" +
                "personId=" + personId +
                ", name='" + name + '\'' +
                ", role=" + role +
                ", deleted=" + deleted +
                '}';
    }
}
