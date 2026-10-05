package com.codingjudge.controller;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wires the submit cooldown end to end: first submit accepted, an
 * immediate second one rejected with 429 + RATE_LIMITED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "judge.cooldown.submit-seconds=60")
@Transactional
class SubmissionRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private Long problemId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        problemRepository.deleteAll();

        Problem problem = new Problem();
        problem.setSlug("ratelimit-sum");
        problem.setTitle("Rate Limit Sum");
        problem.setStatement("Add them.");
        problem.setInputFormat("Two ints.");
        problem.setOutputFormat("Their sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        TestCase sample = new TestCase();
        sample.setInputData("3 4");
        sample.setExpectedOutput("7");
        sample.setSample(true);
        sample.setSortOrder(0);
        problem.addTestCase(sample);
        problemId = problemRepository.save(problem).getId();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cooldown@uni.edu","username":"cooldownuser",
                                 "password":"password123","fullName":"Cooldown"}"""))
                .andExpect(status().isCreated());
        userRepository.findByEmail("cooldown@uni.edu")
                .ifPresent(u -> { u.setEmailVerified(true); userRepository.save(u); });
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cooldown@uni.edu","password":"password123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(login.getResponse().getContentAsString());
        token = json.get("data").get("token").asText();
    }

    @Test
    void immediateSecondSubmitIsRejectedWith429() throws Exception {
        String body = String.format(
                "{\"problemId\":%d,\"language\":\"PYTHON\",\"sourceCode\":\"print(0)\"}", problemId);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }
}
