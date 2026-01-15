package com.example.demo.dto;

import lombok.Builder;
import lombok.NonNull;

import java.time.LocalDate;
import java.util.List;

@Builder
public record TaskDto(
    @NonNull String title,
    LocalDate deadline,
    @NonNull List<SubtaskDto> subtasks
) {}
