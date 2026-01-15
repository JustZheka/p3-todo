package com.example.demo.dto;

import lombok.Builder;
import java.util.UUID;

@Builder
public record SubtaskResponse(
    UUID id,
    String text,
    boolean completed
) {}
