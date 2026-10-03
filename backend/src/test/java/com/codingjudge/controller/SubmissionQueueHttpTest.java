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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the production contract: with the worker enabled, {@code POST
 * /api/submissions} returns {@code 202 + PENDING} immediately instead of
 * executing code inside the HTTP request thread. Judging itself is covered
 * by {@code SubmissionWorkerTest}; the stub judge is never even reached here.
 *
 * <p>The scheduled poll is silenced so nothing judges in the background
 * during these assertions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "judge.worker.enabled=true",
        "judge.worker.poll-ms=3600000"
})
@Transactional
class SubmissionQueueHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    private Long problemId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        problemRepository.deleteAll();

        Problem problem = new Problem();
        problem.setSlug("sum-two");
        problem.setTitle("Sum Two");
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

        token = registerAndLogin("queued@uni.edu", "queued");
    }

    @Test
    void submitReturns202PendingWithoutJudging() throws Exception {
        long started = System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("PYTHON", "print(1)")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        // Immediate: no judging happened inside the request thread.
        assertThat(System.currentTimeMillis() - started).isLessThan(5000);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = json.get("data").get("id").asLong();
        assertThat(submissionRepository.findById(id).orElseThrow().getStatus().name())
                .isEqualTo("PENDING");
    }

    @Test
    void backToBackSubmitsDoNotSerializeOnJudging() throws Exception {
        long started = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/submissions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(submitBody("PYTHON", "print(" + i + ")")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }
        assertThat(System.currentTimeMillis() - started).isLessThan(15000);
        assertThat(submissionRepository.count()).isEqualTo(5);
    }

    private String submitBody(String language, String sourceCode) {
        return String.format("""
                {"problemId": %d, "language": "%s", "sourceCode": "%s"}
                """, problemId, language, sourceCode);
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

        userRepository.findByEmail(email).ifPresent(u -> {
            u.setEmailVerified(true);
            userRepository.save(u);
        });

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
