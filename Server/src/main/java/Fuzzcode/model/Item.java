package Fuzzcode.model;

public record Item(
        int itemId,
        String tagId,
        Position position,   // nullable allowed
        Boolean isOverdue,
        boolean deleted
) {
    @Override
    public String toString() {
        return "Item {" +
                "itemId=" + itemId +
                ", tagId='" + tagId + '\'' +
                ", position=" + position +
                ", isOverdue=" + isOverdue +
                ", deleted=" + deleted +
                '}';
    }
}
