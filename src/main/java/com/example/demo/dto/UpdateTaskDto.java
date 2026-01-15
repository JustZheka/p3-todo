package com.example.demo.dto;

import lombok.Builder;
import java.util.List;

@Builder
public record UpdateTaskDto(
    String title,
    boolean completed,
    List<SubtaskDto> subtasks
) {}
