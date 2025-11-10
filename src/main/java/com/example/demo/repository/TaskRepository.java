package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByldapUid(String ldapUid);
    List<Task> findByldapUidAndCompleted(String ldapUid, boolean completed);
    List<Task> findByldapUidAndDeadline(String ldapUid, LocalDate dueDate);
}
