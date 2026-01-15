package com.example.demo.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    UUID id;

    @Column(nullable = false)
    String title;

    @Builder.Default
    boolean completed = false;

    @Builder.Default
    LocalDate createdAt = LocalDate.now();

    LocalDate deadline;
    String ldapUid;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "task_id")
    @Builder.Default
    List<Subtask> subtasks = new ArrayList<>();
}
