package com.taskforge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.dto.request.ChatRequest;
import com.taskforge.repository.JobRepository;
import com.taskforge.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private com.taskforge.engine.ExecutionEngine executionEngine;

    @BeforeEach
    void setUp() {
        executionEngine.setPaused(true);
        executionEngine.getQueue().clear();
        jobService.clearAllData();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        executionEngine.setPaused(false);
    }

    @Test
    void testChatStudentPersonaVivaQuery() throws Exception {
        ChatRequest request = new ChatRequest("Explain the producer consumer pattern in TaskForge", "STUDENT", null);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona", is("STUDENT")))
                .andExpect(jsonPath("$.reply", containsString("Producer-Consumer Architecture")))
                .andExpect(jsonPath("$.reply", containsString("JobQueue.java")))
                .andExpect(jsonPath("$.systemSnapshot.activeWorkers", notNullValue()));
    }

    @Test
    void testChatOperatorPersonaHealthQuery() throws Exception {
        ChatRequest request = new ChatRequest("Show cluster health and load", "OPERATOR", null);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona", is("OPERATOR")))
                .andExpect(jsonPath("$.reply", containsString("Cluster Telemetry & Health Audit")))
                .andExpect(jsonPath("$.systemSnapshot.queueCapacity", notNullValue()));
    }

    @Test
    void testChatNovicePersonaAnalogyQuery() throws Exception {
        ChatRequest request = new ChatRequest("What is this project in simple words?", "NOVICE", null);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona", is("NOVICE")))
                .andExpect(jsonPath("$.reply", containsString("restaurant kitchen")))
                .andExpect(jsonPath("$.reply", containsString("Chefs")));
    }

    @Test
    void testChatDeveloperPersonaApiQuery() throws Exception {
        ChatRequest request = new ChatRequest("Show me the REST API endpoints and curl examples", "DEVELOPER", null);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona", is("DEVELOPER")))
                .andExpect(jsonPath("$.reply", containsString("/api/v1")))
                .andExpect(jsonPath("$.reply", containsString("curl -X POST")));
    }

    @Test
    void testExecuteActionSubmitSample() throws Exception {
        ChatRequest request = new ChatRequest("run sample", "OPERATOR", "SUBMIT_SAMPLE");

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("Action Executed")))
                .andExpect(jsonPath("$.reply", containsString("5 compute tasks")));
    }

    @Test
    void testGetSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/suggestions")
                        .param("persona", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0]", containsString("producer-consumer")));
    }
}
