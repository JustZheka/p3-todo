package com.example.demo.dto;

import jakarta.validation.Valid;
import java.util.List;
import lombok.Builder;

@Builder
public record UpdateTaskDto(
    String title,
    Boolean completed,
    List<@Valid SubtaskDto> subtasks
) {}
