package com.example.bai5.controller;

import com.example.bai5.dto.ChatbotResponse;
import com.example.bai5.service.TicketRagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketAssistantController {
    private final TicketRagService ticketRagService;

    @PostMapping("/assist")
    public ResponseEntity<ChatbotResponse> assistComplaint(@RequestBody Map<String, String> request) {
        String newComplaint = request.get("complaint");
        if (newComplaint == null || newComplaint.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ChatbotResponse response = ticketRagService.processComplaint(newComplaint);
        return ResponseEntity.ok(response);
    }
}
