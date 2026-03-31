package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.SubtaskDto;
import com.example.demo.dto.SubtaskResponse;
import com.example.demo.dto.TaskDto;
import com.example.demo.dto.TaskResponse;
import com.example.demo.dto.UpdateTaskDto;
import com.example.demo.exception.LdapUidMismatchException;
import com.example.demo.model.RefreshToken;
import com.example.demo.service.JwtService;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.service.TaskService;
import com.example.demo.utils.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ AuthController.class, TaskController.class })
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
        val userRole = "ROLE_TODO_USER";
        val req = LoginRequest.builder()
            .username("alice")
            .password("password")
            .build();

        when(
            authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
            )
        ).thenReturn(
            new UsernamePasswordAuthenticationToken(
                "alice",
                null,
                List.of(new SimpleGrantedAuthority(userRole))
            )
        );
        when(
            jwtService.generateAccessToken("alice", List.of(userRole))
        ).thenReturn("access-token");

        val saved = RefreshToken.builder()
            .id("id-1")
            .username("alice")
            .token("refresh-token")
            .expiry(Instant.now().plusSeconds(3600))
            .revoked(false)
            .build();
        when(
            refreshTokenService.createOrReplace("alice", List.of(userRole))
        ).thenReturn(saved);

        val body = objectMapper.writeValueAsString(req);
        val result = mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().value("refreshToken", "refresh-token"))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().secure("refreshToken", true))
            .andExpect(cookie().attribute("refreshToken", "SameSite", "Strict"))
            .andReturn();

        val json = objectMapper.readTree(
            result.getResponse().getContentAsString()
        );
        accessToken = json.get("accessToken").asText();
        refreshToken = result
            .getResponse()
            .getCookie("refreshToken")
            .getValue();
        loginRefreshEntity = saved;

        when(jwtService.isTokenValid(accessToken)).thenReturn(true);
        when(jwtService.isRefreshToken(accessToken)).thenReturn(false);
        when(jwtService.extractUsername(accessToken)).thenReturn("alice");
        when(jwtService.extractRoles(accessToken)).thenReturn(
            List.of(userRole)
        );

        when(jwtService.isTokenValid(refreshToken)).thenReturn(true);
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUsername(refreshToken)).thenReturn("alice");
        when(jwtService.extractRoles(refreshToken)).thenReturn(
            List.of(userRole)
        );
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/tasks returns user tasks (authorized)")
    void getTasks() throws Exception {
        val firstTask = TaskResponse.builder()
            .id(UUID.randomUUID())
            .title("Task A")
            .deadline(LocalDate.of(2025, 1, 1))
            .build();
        val secondTask = TaskResponse.builder()
            .id(UUID.randomUUID())
            .title("Task B")
            .deadline(LocalDate.of(2025, 2, 2))
            .build();
        when(taskService.getUserTasks()).thenReturn(
            List.of(firstTask, secondTask)
        );

        mockMvc
            .perform(
                get("/api/tasks").header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Task A"))
            .andExpect(jsonPath("$[1].title").value("Task B"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/tasks/{date} returns tasks by date (authorized)")
    void getTasksByDate() throws Exception {
        val date = LocalDate.of(2025, 3, 3);
        val taskByDate = TaskResponse.builder()
            .id(UUID.randomUUID())
            .title("By Date")
            .deadline(date)
            .build();
        when(taskService.getTasksByDate(date)).thenReturn(List.of(taskByDate));

        mockMvc
            .perform(
                get("/api/tasks/" + date).header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].deadline").value(date.toString()))
            .andExpect(jsonPath("$[0].title").value("By Date"));
    }

    @Test
    @Order(4)
    @DisplayName(
        "GET /api/tasks/completed returns completed tasks (authorized)"
    )
    void getCompletedTasks() throws Exception {
        val completedTask = TaskResponse.builder()
            .id(UUID.randomUUID())
            .title("Completed")
            .deadline(LocalDate.of(2025, 4, 4))
            .completed(true)
            .build();
        when(taskService.getCompletedTasks()).thenReturn(
            List.of(completedTask)
        );

        mockMvc
            .perform(
                get("/api/tasks/completed").header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].completed").value(true))
            .andExpect(jsonPath("$[0].title").value("Completed"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/tasks creates a task (authorized)")
    void createTask() throws Exception {
        val subtaskDto = SubtaskDto.builder().text("Subtask 1").build();
        val createTaskDto = TaskDto.builder()
            .title("New Task")
            .deadline(LocalDate.of(2025, 5, 5))
            .subtasks(List.of(subtaskDto))
            .build();

        val createdTask = TaskResponse.builder()
            .id(UUID.randomUUID())
            .title(createTaskDto.title())
            .deadline(createTaskDto.deadline())
            .subtasks(
                List.of(
                    SubtaskResponse.builder()
                        .text("Subtask 1")
                        .completed(false)
                        .build()
                )
            )
            .build();
        when(taskService.createTask(any(TaskDto.class))).thenReturn(
            createdTask
        );

        mockMvc
            .perform(
                post("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createTaskDto))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .with(csrf())
            )
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
        val updatedSubtaskDto = SubtaskDto.builder()
            .text("Updated Subtask")
            .build();
        val updateTaskDto = UpdateTaskDto.builder()
            .title("Updated Title")
            .completed(true)
            .subtasks(List.of(updatedSubtaskDto))
            .build();

        val updatedTask = TaskResponse.builder()
            .id(id)
            .title(updateTaskDto.title())
            .completed(true)
            .deadline(LocalDate.of(2025, 6, 6))
            .subtasks(
                List.of(
                    SubtaskResponse.builder()
                        .text("Updated Subtask")
                        .completed(false)
                        .build()
                )
            )
            .build();
        when(
            taskService.updateTask(eq(id), any(UpdateTaskDto.class))
        ).thenReturn(updatedTask);

        mockMvc
            .perform(
                put("/api/tasks/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateTaskDto))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .with(csrf())
            )
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

        mockMvc
            .perform(
                delete("/api/tasks/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .with(csrf())
            )
            .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    @DisplayName("Refresh returns new access and rotates refresh token")
    void refreshSuccess() throws Exception {
        val userRole = "ROLE_TODO_USER";
        when(refreshTokenService.findValid(refreshToken)).thenReturn(
            Optional.of(loginRefreshEntity)
        );
        when(jwtService.extractUsername(refreshToken)).thenReturn("alice");
        when(jwtService.extractRoles(refreshToken)).thenReturn(
            List.of(userRole)
        );
        when(
            jwtService.generateAccessToken("alice", List.of(userRole))
        ).thenReturn("new-access");
        when(
            refreshTokenService.rotate("alice", refreshToken, List.of(userRole))
        ).thenReturn(
            RefreshToken.builder()
                .id("id-2")
                .username("alice")
                .token("new-refresh")
                .expiry(Instant.now().plusSeconds(10800))
                .revoked(false)
                .build()
        );

        mockMvc
            .perform(
                post("/auth/refresh").cookie(
                    new jakarta.servlet.http.Cookie(
                        "refreshToken",
                        refreshToken
                    )
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().value("refreshToken", "new-refresh"))
            .andExpect(
                cookie().attribute("refreshToken", "SameSite", "Strict")
            );
    }

    @Test
    @Order(9)
    @DisplayName("Logout revokes provided refresh token (authorized)")
    void logout() throws Exception {
        doNothing().when(refreshTokenService).revoke(refreshToken);

        mockMvc
            .perform(
                post("/auth/logout")
                    .cookie(
                        new jakarta.servlet.http.Cookie(
                            "refreshToken",
                            refreshToken
                        )
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("выход выполнен"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(
                cookie().attribute("refreshToken", "SameSite", "Strict")
            );

        verify(refreshTokenService, times(1)).revoke(refreshToken);
    }

    @Test
    @Order(10)
    @DisplayName(
        "PUT /api/tasks/{id} returns 403 when updating another user's task"
    )
    void updateTaskForbidden() throws Exception {
        val id = UUID.randomUUID();
        val updateTaskDto = UpdateTaskDto.builder()
            .title("Hacked Title")
            .completed(true)
            .subtasks(List.of())
            .build();

        when(
            taskService.updateTask(eq(id), any(UpdateTaskDto.class))
        ).thenThrow(new LdapUidMismatchException("LDAP uid не совпадает"));

        mockMvc
            .perform(
                put("/api/tasks/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateTaskDto))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .with(csrf())
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    @DisplayName(
        "DELETE /api/tasks/{id} returns 403 when deleting another user's task"
    )
    void deleteTaskForbidden() throws Exception {
        val id = UUID.randomUUID();

        doThrow(new LdapUidMismatchException("LDAP uid не совпадает"))
            .when(taskService)
            .deleteTask(id);

        mockMvc
            .perform(
                delete("/api/tasks/" + id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .with(csrf())
            )
            .andExpect(status().isForbidden());
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
        public SecurityFilterChain testSecurity(
            final HttpSecurity http,
            final JwtAuthFilter jwtAuthFilter
        ) throws Exception {
            return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                    auth
                        .requestMatchers(
                            "/auth/login",
                            "/auth/logout",
                            "/auth/refresh",
                            "/public/**",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                        )
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated()
                )
                .sessionManagement(session ->
                    session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                    )
                )
                .addFilterBefore(
                    jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class
                )
                .build();
        }
    }
}
