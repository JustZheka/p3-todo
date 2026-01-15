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
    UUID id;

    @Column(nullable = false)
    String text;

    @Builder.Default
    boolean completed = false;
}
