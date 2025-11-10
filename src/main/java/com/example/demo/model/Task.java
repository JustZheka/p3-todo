package com.example.demo.model;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todos")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Builder.Default
    private boolean completed = false;

    @Builder.Default
    private LocalDate createdAt = LocalDate.now();

    private LocalDate deadline;

    private String ldapUid;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "task_id")
    @Builder.Default
    private java.util.List<Subtask> subtasks = new java.util.ArrayList<>();
}
