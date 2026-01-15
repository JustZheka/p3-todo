package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.SubtaskDto;
import com.example.demo.dto.SubtaskResponse;
import com.example.demo.dto.TaskDto;
import com.example.demo.dto.TaskResponse;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.model.RefreshToken;
import com.example.demo.service.JwtService;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.service.TaskService;
import com.example.demo.utils.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
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
        val req = LoginRequest.builder()
                .username("alice")
                .password("password")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", null));
        when(jwtService.generateAccessToken("alice")).thenReturn("access-token");

        val saved = RefreshToken.builder()
                .id("id-1")
                .username("alice")
                .token("refresh-token")
                .expiry(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenService.createOrReplace("alice")).thenReturn(saved);

        val body = objectMapper.writeValueAsString(req);
        val result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andReturn();

        val json = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = json.get("accessToken").asText();
        refreshToken = result.getResponse().getCookie("refreshToken").getValue();
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
        val t1 = TaskResponse.builder()
                .id(UUID.randomUUID())
                .title("Task A")
                .deadline(LocalDate.of(2025, 1, 1))
                .build();
        val t2 = TaskResponse.builder()
                .id(UUID.randomUUID())
                .title("Task B")
                .deadline(LocalDate.of(2025, 2, 2))
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
        val date = LocalDate.of(2025, 3, 3);
        val t = TaskResponse.builder()
                .id(UUID.randomUUID())
                .title("By Date")
                .deadline(date)
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
        val t = TaskResponse.builder()
                .id(UUID.randomUUID())
                .title("Completed")
                .deadline(LocalDate.of(2025, 4, 4))
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
        val sd = SubtaskDto.builder().text("Subtask 1").build();
        val dto = TaskDto.builder()
                .title("New Task")
                .deadline(LocalDate.of(2025, 5, 5))
                .subtasks(List.of(sd))
                .build();

        val created = TaskResponse.builder()
                .id(UUID.randomUUID())
                .title(dto.title())
                .deadline(dto.deadline())
                .subtasks(List.of(SubtaskResponse.builder().text("Subtask 1").completed(false).build()))
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
        val id = UUID.randomUUID();
        val sd = SubtaskDto.builder().text("Updated Subtask").build();
        val dto = UpdateTaskDto.builder()
                .title("Updated Title")
                .completed(true)
                .subtasks(List.of(sd))
                .build();

        val updated = TaskResponse.builder()
                .id(id)
                .title(dto.title())
                .completed(true)
                .deadline(LocalDate.of(2025, 6, 6))
                .subtasks(List.of(SubtaskResponse.builder().text("Updated Subtask").completed(false).build()))
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
        val id = UUID.randomUUID();
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

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().value("refreshToken", "new-refresh"));
    }

    @Test
    @Order(9)
    @DisplayName("Logout revokes provided refresh token (authorized)")
    void logout() throws Exception {
        doNothing().when(refreshTokenService).revoke(refreshToken);

        mockMvc.perform(post("/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("выход выполнен"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().maxAge("refreshToken", 0));

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
        public JwtAuthFilter jwtAuthFilter(final JwtService jwtService) {
            return new JwtAuthFilter(jwtService);
        }

        @Bean
        public SecurityFilterChain testSecurity(final HttpSecurity http, final JwtAuthFilter jwtAuthFilter) throws Exception {
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