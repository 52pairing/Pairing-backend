package com.pairing.template_server.example.application.command;

// Service로 전달되는 DTO (Presentation 기술에 의존하지 않음)
public record CreateExampleCommand(
        String name
) {
}