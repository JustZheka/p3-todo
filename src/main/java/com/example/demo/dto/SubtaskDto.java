package com.example.demo.dto;

import lombok.Builder;
import lombok.NonNull;

@Builder
public record SubtaskDto(
    @NonNull String text
) {}
