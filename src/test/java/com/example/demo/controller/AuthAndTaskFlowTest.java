package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.SubtaskDto;
import com.example.demo.dto.TaskDto;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.Subtask;
import com.example.demo.model.Task;
import com.example.demo.service.JwtService;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.service.TaskService;
import com.example.demo.utils.JwtAuthFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, TaskController.class})
@Import(AuthAndTaskFlowTest.TestConfig.class)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthAndTaskFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private TaskService taskService;

    private String accessToken;
    private String refreshToken;
    private RefreshToken loginRefreshEntity;

    @Test
    @Order(1)
    @DisplayName("Login succeeds and returns tokens (captures for later)")
    void loginSuccess() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", null));
        when(jwtService.generateAccessToken("alice")).thenReturn("access-token");

        RefreshToken saved = RefreshToken.builder()
                .id("id-1")
                .username("alice")
                .token("refresh-token")
                .expiry(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenService.createOrReplace("alice")).thenReturn(saved);

        String body = objectMapper.writeValueAsString(req);
        var result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = json.get("accessToken").asText();
        refreshToken = json.get("refreshToken").asText();
        loginRefreshEntity = saved;

        when(jwtService.isTokenValid(accessToken)).thenReturn(true);
        when(jwtService.isRefreshToken(accessToken)).thenReturn(false);
        when(jwtService.extractUsername(accessToken)).thenReturn("alice");

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUsername(refreshToken)).thenReturn("alice");
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/tasks returns user tasks (authorized)")
    void getTasks() throws Exception {
        Task t1 = Task.builder()
                .id(UUID.randomUUID())
                .title("Task A")
                .deadline(LocalDate.of(2025, 1, 1))
                .ldapUid("alice")
                .build();
        Task t2 = Task.builder()
                .id(UUID.randomUUID())
                .title("Task B")
                .deadline(LocalDate.of(2025, 2, 2))
                .ldapUid("alice")
                .build();
        when(taskService.getUserTasks()).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Task A"))
                .andExpect(jsonPath("$[1].title").value("Task B"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/tasks/{date} returns tasks by date (authorized)")
    void getTasksByDate() throws Exception {
        LocalDate date = LocalDate.of(2025, 3, 3);
        Task t = Task.builder()
                .id(UUID.randomUUID())
                .title("By Date")
                .deadline(date)
                .ldapUid("alice")
                .build();
        when(taskService.getTasksByDate(date)).thenReturn(List.of(t));

        mockMvc.perform(get("/api/tasks/" + date)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deadline").value(date.toString()))
                .andExpect(jsonPath("$[0].title").value("By Date"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/tasks/completed returns completed tasks (authorized)")
    void getCompletedTasks() throws Exception {
        Task t = Task.builder()
                .id(UUID.randomUUID())
                .title("Completed")
                .deadline(LocalDate.of(2025, 4, 4))
                .ldapUid("alice")
                .completed(true)
                .build();
        when(taskService.getCompletedTasks()).thenReturn(List.of(t));

        mockMvc.perform(get("/api/tasks/completed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completed").value(true))
                .andExpect(jsonPath("$[0].title").value("Completed"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/tasks creates a task (authorized)")
    void createTask() throws Exception {
        TaskDto dto = new TaskDto();
        dto.setTitle("New Task");
        dto.setDeadline(LocalDate.of(2025, 5, 5));
        SubtaskDto sd = new SubtaskDto();
        sd.setText("Subtask 1");
        dto.setSubtasks(List.of(sd));

        Task created = Task.builder()
                .id(UUID.randomUUID())
                .title(dto.getTitle())
                .deadline(dto.getDeadline())
                .ldapUid("alice")
                .subtasks(List.of(Subtask.builder().text("Subtask 1").completed(false).build()))
                .build();
        when(taskService.createTask(any(TaskDto.class))).thenReturn(created);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Task"))
                .andExpect(jsonPath("$.deadline").value("2025-05-05"))
                .andExpect(jsonPath("$.subtasks[0].text").value("Subtask 1"));
    }

    @Test
    @Order(6)
    @DisplayName("PUT /api/tasks/{id} updates a task (authorized)")
    void updateTask() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateTaskDto dto = new UpdateTaskDto();
        dto.setTitle("Updated Title");
        dto.setCompleted(true);
        SubtaskDto sd = new SubtaskDto();
        sd.setText("Updated Subtask");
        dto.setSubtasks(List.of(sd));

        Task updated = Task.builder()
                .id(id)
                .title(dto.getTitle())
                .completed(true)
                .deadline(LocalDate.of(2025, 6, 6))
                .ldapUid("alice")
                .subtasks(List.of(Subtask.builder().text("Updated Subtask").completed(false).build()))
                .build();
        when(taskService.updateTask(eq(id), any(UpdateTaskDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.subtasks[0].text").value("Updated Subtask"));
    }

    @Test
    @Order(7)
    @DisplayName("DELETE /api/tasks/{id} deletes a task (authorized)")
    void deleteTask() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(taskService).deleteTask(id);

        mockMvc.perform(delete("/api/tasks/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @DisplayName("Refresh returns new access and rotates refresh token")
    void refreshSuccess() throws Exception {
        when(refreshTokenService.findValid(refreshToken)).thenReturn(Optional.of(loginRefreshEntity));
        when(jwtService.extractUsername(refreshToken)).thenReturn("alice");
        when(jwtService.generateAccessToken("alice")).thenReturn("new-access");
        when(refreshTokenService.rotate("alice", refreshToken)).thenReturn(RefreshToken.builder()
                .id("id-2")
                .username("alice")
                .token("new-refresh")
                .expiry(Instant.now().plusSeconds(10800))
                .revoked(false)
                .build());

        String body = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    @Order(9)
    @DisplayName("Logout revokes provided refresh token (authorized)")
    void logout() throws Exception {
        doNothing().when(refreshTokenService).revoke(refreshToken);

        String body = objectMapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("logged out"));

        verify(refreshTokenService, times(1)).revoke(refreshToken);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public AuthenticationManager authenticationManager() {
            return mock(AuthenticationManager.class);
        }

        @Bean
        public JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        public RefreshTokenService refreshTokenService() {
            return mock(RefreshTokenService.class);
        }

        @Bean
        public TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
            return new JwtAuthFilter(jwtService);
        }

        @Bean
        public SecurityFilterChain testSecurity(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/auth/login", "/auth/refresh", "/public/**").permitAll()
                            .anyRequest().authenticated())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }
}