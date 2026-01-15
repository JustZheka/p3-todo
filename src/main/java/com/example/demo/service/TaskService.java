package com.example.demo.service;

import com.example.demo.dto.TaskDto;
import com.example.demo.dto.TaskResponse;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.exception.LdapUidMismatchException;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.mapper.TaskMapper;
import com.example.demo.repository.TaskRepository;
import lombok.val;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
@Slf4j
public class TaskService {
    TaskRepository taskRepository;
    TaskMapper taskMapper;

    private String getCurrentUsername() {
        return ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName)
                .orElseThrow(() -> new IllegalStateException("Объект аутентификации равен null"));
    }

    public List<TaskResponse> getUserTasks() {
        val username = getCurrentUsername();
        if (log.isDebugEnabled()) {
            log.debug("Поиск задач для пользователя: {}", username);
        }
        return taskMapper.toResponseList(taskRepository.findByldapUid(username));
    }

    public TaskResponse createTask(final TaskDto taskDto) {
        val username = getCurrentUsername();
        val task = taskMapper.toEntity(taskDto, username);
        val saved = taskRepository.save(task);
        if (log.isInfoEnabled()) {
            log.info("Создание задачи {} '{}' для пользователя {}", saved.getId(), saved.getTitle(), username);
        }
        return taskMapper.toResponse(saved);
    }

    public TaskResponse updateTask(final UUID id, final UpdateTaskDto updateTaskDetails) {
        log.info("Обновление задачи {}", id);
        val task = taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException("Задача не найдена"));

        val ldapUid = getCurrentUsername();

        if (!task.getLdapUid().equals(ldapUid)) {
            log.warn("Попытка обновления чужой задачи {} пользователем {}", id, ldapUid);
            throw new LdapUidMismatchException("LDAP uid не совпадает");
        }

        taskMapper.updateTaskFromDto(updateTaskDetails, task);

        return taskMapper.toResponse(taskRepository.save(task));
    }

    public void deleteTask(final UUID id) {
        log.info("Удаление задачи {}", id);
        taskRepository.deleteById(id);
    }

    public List<TaskResponse> getCompletedTasks() {
        val username = getCurrentUsername();
        if (log.isDebugEnabled()) {
            log.debug("Поиск выполненных задач для пользователя: {}", username);
        }
        return taskMapper.toResponseList(taskRepository.findByldapUidAndCompleted(username, true));
    }

    public List<TaskResponse> getTasksByDate(final LocalDate date) {
        val username = getCurrentUsername();
        if (log.isDebugEnabled()) {
            log.debug("Поиск задач на дату {} для пользователя: {}", date, username);
        }
        return taskMapper.toResponseList(taskRepository.findByldapUidAndDeadline(username, date));
    }
}
