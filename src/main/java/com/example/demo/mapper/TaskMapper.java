package com.example.demo.mapper;

import com.example.demo.dto.SubtaskDto;
import com.example.demo.dto.SubtaskResponse;
import com.example.demo.dto.TaskDto;
import com.example.demo.dto.TaskResponse;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.model.Subtask;
import com.example.demo.model.Task;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", constant = "false")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDate.now())")
    @Mapping(target = "subtasks", source = "taskDto.subtasks")
    @Mapping(target = "ldapUid", source = "ldapUid")
    Task toEntity(TaskDto taskDto, String ldapUid);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "completed", constant = "false")
    Subtask toEntity(SubtaskDto subtaskDto);

    List<Subtask> toSubtaskList(List<SubtaskDto> subtaskDtos);

    TaskResponse toResponse(Task task);

    List<TaskResponse> toResponseList(List<Task> tasks);

    SubtaskResponse toSubtaskResponse(Subtask subtask);

    List<SubtaskResponse> toSubtaskResponseList(List<Subtask> subtasks);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ldapUid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deadline", ignore = true)
    void updateTaskFromDto(UpdateTaskDto dto, @MappingTarget Task task);

    @Condition
    default boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
