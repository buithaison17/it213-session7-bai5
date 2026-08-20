package com.example.bai5.dto;

public record TicketDto(
        String ticketId,
        String customerIssue,
        String resolution,
        double similarityScore
) {
}
