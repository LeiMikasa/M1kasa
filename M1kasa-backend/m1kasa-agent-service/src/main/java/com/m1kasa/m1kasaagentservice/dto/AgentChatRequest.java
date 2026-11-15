package com.m1kasa.m1kasaagentservice.dto;

public record AgentChatRequest(
            String conversationId,
            String message,
            Double longitude,
            Double latitude
    ) {
    }
