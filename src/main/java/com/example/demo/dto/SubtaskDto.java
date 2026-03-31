package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record SubtaskDto(@NotBlank String text) {}
