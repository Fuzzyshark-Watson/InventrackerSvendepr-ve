package Fuzzcode.Server.model;

public record OrderItem(
        int orderId,
        int itemId,
        boolean deleted
) {
    @Override
    public String toString() {
        return "OrderItem { " +
                "orderId=" + orderId +
                ", itemId=" + itemId +
                ", deleted=" + deleted +
                '}';
    }
}
