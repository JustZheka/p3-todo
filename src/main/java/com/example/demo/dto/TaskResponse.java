package com.example.demo.dto;

import lombok.Builder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
public record TaskResponse(
    UUID id,
    String title,
    boolean completed,
    LocalDate createdAt,
    LocalDate deadline,
    List<SubtaskResponse> subtasks
) {}
