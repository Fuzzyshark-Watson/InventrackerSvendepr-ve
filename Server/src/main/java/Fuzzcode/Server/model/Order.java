package Fuzzcode.Server.model;

import java.time.LocalDate;

public record Order(
        int orderId,
        LocalDate createdDate,
        LocalDate startDate,
        LocalDate endDate,
        Integer customerId,
        Integer loggedById,
        boolean deleted
) {
    @Override
    public String toString() {
        return "Order {" +
                "orderId=" + orderId +
                ", createdDate=" + createdDate +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", customerId=" + customerId +
                ", loggedById=" + loggedById +
                ", deleted=" + deleted +
                '}';
    }
}