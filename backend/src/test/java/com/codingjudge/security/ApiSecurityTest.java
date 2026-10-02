package com.codingjudge.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security regression tests for the API surface.
 *
 * These lock in the behaviours verified manually during the Phase 13 audit so a
 * future refactor cannot silently weaken them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiSecurityTest {

    private static final String HIDDEN_INPUT = "SECRET_HIDDEN_INPUT_zz11";
    private static final String HIDDEN_OUTPUT = "SECRET_HIDDEN_OUTPUT_zz22";

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
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        problemRepository.deleteAll();

        Problem problem = new Problem();
        problem.setSlug("sec-problem");
        problem.setTitle("Security Problem");
        problem.setStatement("Statement");
        problem.setInputFormat("Input");
        problem.setOutputFormat("Output");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");

        TestCase sample = new TestCase();
        sample.setInputData("visible input");
        sample.setExpectedOutput("visible output");
        sample.setSample(true);
        sample.setSortOrder(0);
        problem.addTestCase(sample);

        TestCase hidden = new TestCase();
        hidden.setInputData(HIDDEN_INPUT);
        hidden.setExpectedOutput(HIDDEN_OUTPUT);
        hidden.setSample(false);
        hidden.setSortOrder(1);
        problem.addTestCase(hidden);

        problemId = problemRepository.save(problem).getId();

        tokenA = registerAndLogin("owner@uni.edu", "owneruser");
        tokenB = registerAndLogin("attacker@uni.edu", "attackeruser");
    }

    @Test
    void protectedEndpoints_rejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/problems")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":1,\"language\":\"JAVA\",\"sourceCode\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_rejectMalformedTokens() throws Exception {
        for (String header : new String[]{"Bearer garbage", "Bearer a.b.c", "garbage"}) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void algNoneToken_isRejected() throws Exception {
        // Classic JWT downgrade: {"alg":"none","typ":"JWT"}
        String token = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0."
                + "eyJzdWIiOiJvd25lckB1bmkuZWR1In0.";
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void problemDetail_neverExposesHiddenTestCases() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/problems/sec-problem")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sampleTestCases.length()").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT);
        assertThat(body).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    void submissionList_neverExposesSourceCode() throws Exception {
        submit(tokenA, "public class Main {}");

        MvcResult result = mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("sourceCode");
    }

    @Test
    void submissionDetail_neverExposesHiddenTestCases() throws Exception {
        long id = submit(tokenA, "public class Main {}");

        MvcResult result = mockMvc.perform(get("/api/submissions/" + id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testResults.length()").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT);
        assertThat(body).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    void userCannotReadAnotherUsersSubmission() throws Exception {
        long id = submit(tokenA, "public class Main {}");

        mockMvc.perform(get("/api/submissions/" + id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void submissionList_isScopedToTheAuthenticatedUser() throws Exception {
        submit(tokenA, "public class Main {}");
        submit(tokenA, "public class Main {}");
        submit(tokenB, "public class Main {}");

        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void oversizeSourceCode_isRejected() throws Exception {
        String oversized = "A".repeat(300 * 1024);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"problemId\":%d,\"language\":\"JAVA\",\"sourceCode\":\"%s\"}",
                                problemId, oversized)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void blankSourceCode_isRejected() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"problemId\":%d,\"language\":\"JAVA\",\"sourceCode\":\"   \"}",
                                problemId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidLanguage_isRejected() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"problemId\":%d,\"language\":\"SHELL\",\"sourceCode\":\"rm -rf /\"}",
                                problemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void passwordIsNeverReturnedByAnyEndpoint() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"fresh@uni.edu","username":"freshuser",
                                 "password":"password123","fullName":"Fresh"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String body = register.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password123");
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("password_hash");
    }

    @Test
    void passwordIsStoredAsHashNotPlaintext() throws Exception {
        registerAndLogin("hashcheck@uni.edu", "hashcheck");

        var stored = userRepository.findByEmail("hashcheck@uni.edu").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("password123");
        assertThat(stored.getPasswordHash()).startsWith("$2");
    }

    private long submit(String token, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"problemId\":%d,\"language\":\"JAVA\",\"sourceCode\":\"%s\"}",
                                problemId, sourceCode)))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("id").asLong();
    }

    private String registerAndLogin(String email, String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","username":"%s","password":"password123",
                                 "fullName":"Test User"}""", email, username)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","password":"password123"}""", email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("token").asText();
    }
}
