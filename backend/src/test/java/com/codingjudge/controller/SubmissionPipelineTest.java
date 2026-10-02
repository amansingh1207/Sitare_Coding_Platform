package com.codingjudge.controller;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
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
class SubmissionPipelineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        problem.setSlug("power-cut");
        problem.setTitle("Power Cut");
        problem.setStatement("Statement");
        problem.setInputFormat("Input format");
        problem.setOutputFormat("Output format");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");

        // Add test cases so judge has something to evaluate
        TestCase sampleCase = new TestCase();
        sampleCase.setInputData("public class Main {}");
        sampleCase.setExpectedOutput("public class Main {}");
        sampleCase.setSample(true);
        sampleCase.setSortOrder(0);
        problem.addTestCase(sampleCase);

        TestCase hiddenCase = new TestCase();
        hiddenCase.setInputData("test input");
        hiddenCase.setExpectedOutput("test input");
        hiddenCase.setSample(false);
        hiddenCase.setSortOrder(1);
        problem.addTestCase(hiddenCase);

        problemId = problemRepository.save(problem).getId();

        tokenA = registerAndLogin("author@uni.edu", "author");
        tokenB = registerAndLogin("other@uni.edu", "other");
    }

    @Test
    void submitSuccessReturns202Pending() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("JAVA", "public class Main {}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = json.get("data").get("id").asLong();
        assertThat(submissionRepository.findById(id)).isPresent();
        assertThat(submissionRepository.findById(id).orElseThrow().getSourceCode())
                .isEqualTo("public class Main {}");
    }

    @Test
    void submitRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("JAVA", "code")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitMissingProblemReturns404() throws Exception {
        String body = String.format("""
                {"problemId": 999999, "language": "JAVA", "sourceCode": "code"}
                """);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void submitInvalidLanguageReturns400() throws Exception {
        String body = String.format("""
                {"problemId": %d, "language": "RUBY", "sourceCode": "code"}
                """, problemId);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submitBlankCodeReturns400() throws Exception {
        String body = String.format("""
                {"problemId": %d, "language": "PYTHON", "sourceCode": "  "}
                """, problemId);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitOversizeCodeReturns413() throws Exception {
        String bigCode = "x".repeat(300 * 1024);
        String body = String.format("""
                {"problemId": %d, "language": "JAVA", "sourceCode": "%s"}
                """, problemId, bigCode);

        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void getOwnSubmissionShowsSourceCode() throws Exception {
        long id = submit(tokenA, "JAVA", "public class Main {}");

        mockMvc.perform(get("/api/submissions/" + id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.language").value("JAVA"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.sourceCode").value("public class Main {}"))
                .andExpect(jsonPath("$.data.problem.slug").value("power-cut"))
                .andExpect(jsonPath("$.data.testResults").isArray());
    }

    @Test
    void getOtherUsersSubmissionReturns403() throws Exception {
        long id = submit(tokenA, "JAVA", "code");

        mockMvc.perform(get("/api/submissions/" + id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void getMissingSubmissionReturns404() throws Exception {
        mockMvc.perform(get("/api/submissions/999999")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void listShowsOnlyOwnSubmissions() throws Exception {
        submit(tokenA, "JAVA", "a1");
        submit(tokenA, "JAVA", "a2");

        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        MvcResult result = mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"sourceCode\"");
    }

    @Test
    void listFiltersByProblemStatusAndLanguage() throws Exception {
        submit(tokenA, "JAVA", "a1");
        submit(tokenA, "JAVA", "a2");

        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("language", "JAVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("status", "ACCEPTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("problemId", String.valueOf(problemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/submissions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/submissions"))
                .andExpect(status().isUnauthorized());
    }

    private String submitBody(String language, String sourceCode) {
        return String.format("""
                {"problemId": %d, "language": "%s", "sourceCode": "%s"}
                """, problemId, language, sourceCode);
    }

    private long submit(String token, String language, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(language, sourceCode)))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("id").asLong();
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
