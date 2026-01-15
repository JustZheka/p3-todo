package com.example.demo.repository;

import com.example.demo.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByldapUid(final String ldapUid);
    List<Task> findByldapUidAndCompleted(final String ldapUid, final boolean completed);
    List<Task> findByldapUidAndDeadline(final String ldapUid, final LocalDate dueDate);
}
