package com.example.bai5.dto;

import java.util.List;

public record ChatbotResponse(
        String draftEmail,
        List<TicketDto> references
) {
}
