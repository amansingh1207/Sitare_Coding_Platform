package com.codingjudge.controller;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemIntegrationTest {

    private static final String HIDDEN_INPUT = "hidden_input_9f8e7d6c";
    private static final String HIDDEN_OUTPUT = "hidden_output_1a2b3c4d";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        problemRepository.deleteAll();

        Problem week1 = newProblem("power-cut", "Power Cut", Difficulty.EASY, "Week 1");
        addCase(week1, "2\n3", "6", true, 0);
        addCase(week1, HIDDEN_INPUT, HIDDEN_OUTPUT, false, 1);
        problemRepository.save(week1);

        Problem week2 = newProblem("hackathon-leaderboard", "Hackathon Leaderboard",
                Difficulty.MEDIUM, "Week 2");
        addCase(week2, "5", "25", true, 0);
        problemRepository.save(week2);

        token = registerAndLogin("problems@uni.edu", "problemuser");
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listProblemsReturnsPaginatedEnvelope() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void searchByTitle() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("hackathon-leaderboard"));
    }

    @Test
    void filterByWeek() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token)
                        .param("week", "Week 1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("power-cut"));
    }

    @Test
    void filterByDifficulty() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token)
                        .param("difficulty", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("hackathon-leaderboard"));
    }

    @Test
    void invalidDifficultyReturns400() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token)
                        .param("difficulty", "IMPOSSIBLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getProblemDetailShowsSamplesOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/problems/power-cut")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("power-cut"))
                .andExpect(jsonPath("$.data.title").value("Power Cut"))
                .andExpect(jsonPath("$.data.statement").isNotEmpty())
                .andExpect(jsonPath("$.data.inputFormat").isNotEmpty())
                .andExpect(jsonPath("$.data.outputFormat").isNotEmpty())
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.weekLabel").value("Week 1"))
                .andExpect(jsonPath("$.data.timeLimitMs").value(2000))
                .andExpect(jsonPath("$.data.memoryLimitMb").value(256))
                .andExpect(jsonPath("$.data.sampleTestCases.length()").value(1))
                .andExpect(jsonPath("$.data.sampleTestCases[0].inputData").value("2\n3"))
                .andExpect(jsonPath("$.data.sampleTestCases[0].expectedOutput").value("6"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT);
        assertThat(body).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    void listNeverExposesHiddenTests() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/problems")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT);
        assertThat(body).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    void getMissingProblemReturns404() throws Exception {
        mockMvc.perform(get("/api/problems/no-such-problem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private Problem newProblem(String slug, String title, Difficulty difficulty, String week) {
        Problem problem = new Problem();
        problem.setSlug(slug);
        problem.setTitle(title);
        problem.setStatement("Statement for " + title);
        problem.setInputFormat("Input format");
        problem.setOutputFormat("Output format");
        problem.setConstraints("1 <= n <= 100");
        problem.setDifficulty(difficulty);
        problem.setWeekLabel(week);
        return problem;
    }

    private void addCase(Problem problem, String input, String output, boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setInputData(input);
        testCase.setExpectedOutput(output);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        problem.addTestCase(testCase);
    }

    private String registerAndLogin(String email, String username) throws Exception {
        String registerBody = String.format("""
                {
                  "email": "%s",
                  "username": "%s",
                  "password": "password123",
                  "fullName": "Test User"
                }
                """, email, username);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        userRepository.findByEmail(email).ifPresent(u -> { u.setEmailVerified(true); userRepository.save(u); });

        String loginBody = String.format("""
                {
                  "email": "%s",
                  "password": "password123"
                }
                """, email);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("token").asText();
    }
}
