package com.example.demo.controller;

import com.example.demo.dto.TaskDto;
import com.example.demo.dto.TaskResponse;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.service.TaskService;

import lombok.val;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true)
@Slf4j
public class TaskController {
    TaskService taskService;

    @GetMapping
    public List<TaskResponse> getTasks() {
        if (log.isDebugEnabled()) {
            log.debug("Запрос на получение списка задач");
        }
        return taskService.getUserTasks();
    }

    @GetMapping("/{date}")
    public List<TaskResponse> getTaskByDate(final @PathVariable LocalDate date) {
        if (log.isDebugEnabled()) {
            log.debug("Запрос на получение задач на дату: {}", date);
        }
        return taskService.getTasksByDate(date);
    }

    @GetMapping("/completed")
    public List<TaskResponse> getCompletedTasks() {
        if (log.isDebugEnabled()) {
            log.debug("Запрос на получение выполненных задач");
        }
        return taskService.getCompletedTasks();
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(final @RequestBody TaskDto taskDto) {
        val createdTask = taskService.createTask(taskDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTask);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(final @PathVariable UUID id, final @RequestBody UpdateTaskDto updateTaskDto) {
        log.info("Обновление задачи с id: {}", id);
        return ResponseEntity.ok(taskService.updateTask(id, updateTaskDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(final @PathVariable UUID id) {
        log.info("Удаление задачи с id: {}", id);
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }
}
