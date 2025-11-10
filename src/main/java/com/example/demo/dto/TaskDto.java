package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TaskDto {
    private String title;
    private LocalDate deadline;
    private List<SubtaskDto> subtasks;
}
