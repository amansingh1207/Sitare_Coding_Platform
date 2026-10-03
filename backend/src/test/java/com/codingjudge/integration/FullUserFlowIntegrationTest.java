package com.codingjudge.integration;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.codingjudge.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * End-to-end coverage of the flow a student actually performs:
 * register, login, browse, open a problem, run sample code, submit, read the
 * verdict, and review history.
 *
 * The judge's Docker layer is stubbed (see TestJudgeConfig) because a test suite
 * must not require a Docker daemon; the live container path is verified
 * separately and recorded in docs/JUDGE_DESIGN.md section 11.5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Full user flow")
class FullUserFlowIntegrationTest {

    private static final String SAMPLE_INPUT = "3 4";
    private static final String SAMPLE_OUTPUT = "7";
    private static final String HIDDEN_INPUT = "999 1111";
    private static final String HIDDEN_OUTPUT = "2110";
    /** Unmarked source: the stub treats it as a correct solution to the fixture problem. */
    private static final String SOLUTION = "public class Main { }";

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

    private Long sumProblemId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        submissionRepository.deleteAll();
        problemRepository.deleteAll();

        sumProblemId = saveProblem("sum-two", "Sum of Two Numbers", Difficulty.EASY).getId();

        Problem fizzbuzz = new Problem();
        fizzbuzz.setSlug("fizzbuzz");
        fizzbuzz.setTitle("FizzBuzz");
        fizzbuzz.setStatement("Print FizzBuzz");
        fizzbuzz.setInputFormat("n");
        fizzbuzz.setOutputFormat("line");
        fizzbuzz.setDifficulty(Difficulty.MEDIUM);
        fizzbuzz.setWeekLabel("Week 2");
        problemRepository.save(fizzbuzz);

        Problem solution = new Problem();
        solution.setSlug("a-plus-b-again");
        solution.setTitle("Sum of Two Numbers (Revisited)");
        solution.setStatement("Add two numbers again");
        solution.setInputFormat("a b");
        solution.setOutputFormat("sum");
        solution.setDifficulty(Difficulty.HARD);
        solution.setWeekLabel("Week 3");
        problemRepository.save(solution);

        token = registerAndLogin("student@uni.edu", "student");
    }

    @Test
    @DisplayName("register, login and /me return the same identity")
    void registerLoginAndIdentity() throws Exception {
        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("student@uni.edu"))
                .andExpect(jsonPath("$.data.username").value("student"))
                .andReturn();

        assertThat(me.getResponse().getContentAsString()).doesNotContain("password");
        assertThat(userRepository.findByEmail("student@uni.edu").orElseThrow().getPasswordHash())
                .startsWith("$2");
    }

    @Test
    @DisplayName("duplicate registration is rejected")
    void duplicateRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@uni.edu","username":"other",
                                 "password":"password123","fullName":"Other"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("browsing lists problems and supports search and filters")
    void browseProblems() throws Exception {
        mockMvc.perform(get("/api/problems").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/api/problems").param("search", "fizz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("fizzbuzz"));

        mockMvc.perform(get("/api/problems").param("difficulty", "HARD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("a-plus-b-again"));
    }

    @Test
    @DisplayName("opening a problem shows samples and hides the rest")
    void openProblemDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/problems/sum-two")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sampleTestCases.length()").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(SAMPLE_INPUT).contains(SAMPLE_OUTPUT);
        assertThat(body).doesNotContain(HIDDEN_INPUT).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    @DisplayName("run executes against samples and returns the expected output")
    void runAgainstSamples() throws Exception {
        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("JAVA", SOLUTION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.testResults.length()").value(1))
                .andExpect(jsonPath("$.data.testResults[0].actualOutput").value(SAMPLE_OUTPUT))
                .andExpect(jsonPath("$.data.testResults[0].expectedOutput").value(SAMPLE_OUTPUT))
                .andExpect(jsonPath("$.data.testResults[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.totalRuntimeMs").value(100));
    }

    @Test
    @DisplayName("run reports a wrong answer without creating a submission")
    void runWrongAnswer() throws Exception {
        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("JAVA", "// /*WRONG*/")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.testResults[0].status").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.data.testResults[0].actualOutput")
                        .value("definitely not the answer"));

        assertThat(submissionRepository.count()).as("run must not record a submission").isZero();
    }

    @Test
    @DisplayName("run surfaces compiler diagnostics")
    void runReportsCompilationError() throws Exception {
        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("JAVA", "public class Main { /*CE*/ }")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPILATION_ERROR"))
                .andExpect(jsonPath("$.data.testResults[0].status").value("COMPILATION_ERROR"))
                .andExpect(jsonPath("$.data.testResults[0].actualOutput")
                        .value("Main.java:3: error: ';' expected"));
    }

    @Test
    @DisplayName("run reports runtime and limit failures")
    void runReportsRuntimeAndLimitFailures() throws Exception {
        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("PYTHON", "# /*RE*/")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.data.testResults[0].actualOutput")
                        .value("Exception in thread \"main\" java.lang.NullPointerException"));

        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("CPP", "// /*TLE*/")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TIME_LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("CPP", "// /*OOM*/")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MEMORY_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("run never reveals hidden test cases")
    void runNeverExposesHiddenTests() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("JAVA", SOLUTION)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT).doesNotContain(HIDDEN_OUTPUT);
    }

    @Test
    @DisplayName("run requires authentication and a valid payload")
    void runInputValidation() throws Exception {
        mockMvc.perform(post("/api/submissions/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("JAVA", "public class Main {}")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("RUST", "fn main() {}")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/submissions/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":999999,\"language\":\"JAVA\",\"sourceCode\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("submitting a correct solution yields ACCEPTED with metrics")
    void submitAcceptedSolution() throws Exception {
        long id = submit("JAVA", SOLUTION);

        mockMvc.perform(get("/api/submissions/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.language").value("JAVA"))
                .andExpect(jsonPath("$.data.problem.slug").value("sum-two"))
                .andExpect(jsonPath("$.data.runtimeMs").value(100))
                .andExpect(jsonPath("$.data.memoryUsedKb").value(10240))
                .andExpect(jsonPath("$.data.testResults.length()").value(1))
                .andExpect(jsonPath("$.data.testResults[0].status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("a submission failing only on hidden tests is judged, and stays quiet about them")
    void submitFailingOnHiddenTest() throws Exception {
        // Unmarked source solves the problem, so this passes both known cases.
        long id = submit("JAVA", SOLUTION);
        JsonNode detail = getJson("/api/submissions/" + id);

        // Add a hidden case the solver cannot satisfy.
        Problem problem = problemRepository.findById(sumProblemId).orElseThrow();
        TestCase hidden = new TestCase();
        hidden.setInputData("10 20");
        hidden.setExpectedOutput("999");
        hidden.setSample(false);
        hidden.setSortOrder(2);
        problem.addTestCase(hidden);
        problemRepository.save(problem);

        long failingId = submit("JAVA", SOLUTION);
        MvcResult result = mockMvc.perform(get("/api/submissions/" + failingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WRONG_ANSWER"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("999").doesNotContain(HIDDEN_INPUT);
        assertThat(detail.get("data").get("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("submission detail shows the compiler message instead of an empty box")
    void submitSurfacesCompilationError() throws Exception {
        long id = submit("JAVA", "public class Main { /*CE*/ }");

        mockMvc.perform(get("/api/submissions/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPILATION_ERROR"))
                .andExpect(jsonPath("$.data.testResults[0].actualOutput")
                        .value("Main.java:3: error: ';' expected"));
    }

    @Test
    @DisplayName("history lists the student's submissions newest first")
    void submissionHistory() throws Exception {
        submit("JAVA", "public class Main { /*CE*/ }");
        submit("CPP", "// /*TLE*/");
        submit("PYTHON", "# /*RE*/");

        mockMvc.perform(get("/api/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(3));

        // History must not carry the full source, only a summary.
        String list = getJsonString("/api/submissions");
        assertThat(list).doesNotContain("sourceCode");
    }

    @Test
    @DisplayName("history filters by problem, status and language")
    void submissionHistoryFilters() throws Exception {
        submit("JAVA", "public class Main { /*CE*/ }");
        submit("CPP", "// /*TLE*/");

        mockMvc.perform(get("/api/submissions").param("problemId", String.valueOf(sumProblemId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/submissions").param("status", "COMPILATION_ERROR")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].language").value("JAVA"));

        mockMvc.perform(get("/api/submissions").param("language", "CPP")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("TIME_LIMIT_EXCEEDED"));

        mockMvc.perform(get("/api/submissions").param("status", "NOT_A_STATUS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pagination is clamped to sane bounds")
    void historyPaginationIsBounded() throws Exception {
        mockMvc.perform(get("/api/submissions")
                        .param("page", "-5").param("size", "1000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("one student never sees another's work")
    void submissionsAreIsolatedPerUser() throws Exception {
        long mine = submit("JAVA", SOLUTION);

        String otherToken = registerAndLogin("other@uni.edu", "otheruser");
        submitAs(otherToken, "CPP", "// /*TLE*/");

        mockMvc.perform(get("/api/submissions/" + mine)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/submissions").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("all three languages are accepted end to end")
    void allSupportedLanguagesSubmit() throws Exception {
        for (String language : new String[]{"JAVA", "CPP", "PYTHON"}) {
            long id = submit(language, "source for " + language);
            mockMvc.perform(get("/api/submissions/" + id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.language").value(language))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        }
    }

    private Problem saveProblem(String slug, String title, Difficulty difficulty) {
        Problem problem = new Problem();
        problem.setSlug(slug);
        problem.setTitle(title);
        problem.setStatement("Read two integers and print their sum.");
        problem.setInputFormat("Two space-separated integers a and b.");
        problem.setOutputFormat("One integer: a + b.");
        problem.setDifficulty(difficulty);
        problem.setWeekLabel("Week 1");

        TestCase sample = new TestCase();
        sample.setInputData(SAMPLE_INPUT);
        sample.setExpectedOutput(SAMPLE_OUTPUT);
        sample.setSample(true);
        sample.setSortOrder(0);
        problem.addTestCase(sample);

        TestCase hidden = new TestCase();
        hidden.setInputData(HIDDEN_INPUT);
        hidden.setExpectedOutput(HIDDEN_OUTPUT);
        hidden.setSample(false);
        hidden.setSortOrder(1);
        problem.addTestCase(hidden);

        return problemRepository.save(problem);
    }

    private long submit(String language, String sourceCode) throws Exception {
        return submitAs(token, language, sourceCode);
    }

    private long submitAs(String authToken, String language, String sourceCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/submissions")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"problemId\":%d,\"language\":\"%s\",\"sourceCode\":%s}",
                                sumProblemId, language, quote(sourceCode))))
                .andExpect(status().isAccepted())
                .andReturn();
        return read(result).get("data").get("id").asLong();
    }

    private String runBody(String language, String sourceCode) {
        return String.format(
                "{\"problemId\":%d,\"language\":\"%s\",\"sourceCode\":%s}",
                sumProblemId, language, quote(sourceCode));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        return read(mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String getJsonString(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Minimal JSON string escaping for embedding source code in a request body. */
    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String registerAndLogin(String email, String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"email":"%s","username":"%s","password":"password123",
                                 "fullName":"Test User"}""", email, username)))
                .andExpect(status().isCreated());

        userRepository.findByEmail(email).ifPresent(u -> { u.setEmailVerified(true); userRepository.save(u); });

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"password123\"}", email)))
                .andExpect(status().isOk())
                .andReturn();
        return read(login).get("data").get("token").asText();
    }
}