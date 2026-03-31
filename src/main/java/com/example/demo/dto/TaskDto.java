package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record TaskDto(
    @NotBlank String title,
    LocalDate deadline,
    List<@Valid SubtaskDto> subtasks
) {}
