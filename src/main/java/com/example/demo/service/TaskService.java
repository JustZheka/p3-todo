package com.example.demo.service;

import com.example.demo.dto.SubtaskDto;
import com.example.demo.dto.TaskDto;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.model.Subtask;
import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository taskRepository;

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null)
            throw new IllegalStateException("Authentication object is null");

        return auth.getName();
    }

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> getUserTasks() {
        return taskRepository.findByldapUid(getCurrentUsername());
    }

    private List<Subtask> mapToSubtasks(List<SubtaskDto>  subtaskDtos) {
        return subtaskDtos.stream().map(
            st -> {
                Subtask subtask = Subtask.builder()
                        .text(st.getText())
                        .build();

                return subtask;
            }
        )
        .toList();
    }

    private Task mapToTask(TaskDto taskDto, String LDAPuid) {
        Task task = Task.builder()
                .title(taskDto.getTitle())
                .deadline(taskDto.getDeadline())
                .ldapUid(LDAPuid)
                .build();

        if (taskDto.getSubtasks() != null && !taskDto.getSubtasks().isEmpty()) {
            List<Subtask> subtasks = mapToSubtasks(taskDto.getSubtasks());

            task.setSubtasks(subtasks);
        }

        return task;
    }

    public Task createTask(TaskDto taskDto) {
        return taskRepository.save(mapToTask(taskDto, getCurrentUsername()));
    }

    public Task updateTask(UUID id, UpdateTaskDto updateTaskDetails) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Task not found"));

        String LDAPuid = getCurrentUsername();

        if (!task.getLdapUid().equals(LDAPuid)) {
            throw new RuntimeException("LDAPuid not match");
        }

        task.setCompleted(updateTaskDetails.isCompleted());
        task.setTitle(updateTaskDetails.getTitle());

        if (updateTaskDetails.getSubtasks() != null && !updateTaskDetails.getSubtasks().isEmpty()) {
            task.setSubtasks(mapToSubtasks(updateTaskDetails.getSubtasks()));
        }

        return taskRepository.save(task);
    }

    public void deleteTask(UUID id) {
        taskRepository.deleteById(id);
    }

    public List<Task> getCompletedTasks() {
        return taskRepository.findByldapUidAndCompleted(getCurrentUsername(), true);
    }

    public List<Task> getTasksByDate(LocalDate date) {
        return taskRepository.findByldapUidAndDeadline(getCurrentUsername(), date);
    }
}
