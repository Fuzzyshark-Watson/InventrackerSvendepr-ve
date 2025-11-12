package Fuzzcode.model;

import java.time.Instant;

public record ItemRead(
        int readId,
        String tagId,
        Instant readTime,
        boolean deleted
) {
    @Override
    public String toString() {
        return "ItemRead {" +
                "readId=" + readId +
                ", tagId='" + tagId + '\'' +
                ", readTime=" + readTime +
                ", deleted=" + deleted +
                '}';
    }
}
