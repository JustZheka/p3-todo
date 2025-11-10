package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateTaskDto {
    private String title;
    private boolean completed;
    private List<SubtaskDto> subtasks;
}
