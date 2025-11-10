package com.example.demo.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subtasks")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Subtask {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String text;

    @Builder.Default
    private boolean completed = false;
}
