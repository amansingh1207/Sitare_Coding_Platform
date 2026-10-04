package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.model.enums.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Server-free tests for {@link DomjudgeExecutionService}: run mapping,
 * subset alignment, failure containment and the run-custom limitation.
 * Live end-to-end coverage lives in the gated live test.
 */
@ExtendWith(MockitoExtension.class)
class DomjudgeExecutionServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Scripted fake: canned judgement, runs and languages. */
    static class FakeClient extends DomjudgeClient {
        String judgement = "{\"id\": \"1\", \"judgement_type_id\": \"AC\"}";
        List<RunResult> runs = List.of();
        /** When non-empty, each listRuns call consumes the head (slow-flush scripts). */
        final java.util.Queue<List<RunResult>> runsScript = new java.util.ArrayDeque<>();
        int submits;

        FakeClient() {
            super("http://domjudge.invalid", "u", "p");
        }

        @Override
        public String submit(String contest, String languageId, String problemDomId,
                             String filename, String source, String entryPoint) {
            submits++;
            return "s1";
        }

        @Override
        public JsonNode awaitJudgement(String contest, String submissionId,
                                       long pollIntervalMs, long maxWaitMs) {
            return json(judgement);
        }

        @Override
        public List<RunResult> listRuns(String contest, String judgementId) {
            List<RunResult> next = runsScript.poll();
            return next != null ? next : runs;
        }

        @Override
        public List<LanguageInfo> listLanguages() {
            return List.of(
                    new LanguageInfo("cpp", "C++"),
                    new LanguageInfo("java", "Java"),
                    new LanguageInfo("python3", "Python 3"));
        }

        @Override
        public String importProblem(String contest, String zipFilename, byte[] packageZip) {
            return "dom-1";
        }

        @Override
        public List<ProblemInfo> listProblems(String contest) {
            return List.of();
        }
    }

    static DomjudgeClient.RunResult run(int ordinal, String verdict, double seconds) {
        return new DomjudgeClient.RunResult("r" + ordinal, ordinal, verdict, seconds);
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setId(3L);
        problem.setSlug("sum");
        problem.setTitle("Sum");
        problem.setStatement("Add.");
        problem.setInputFormat("Ints.");
        problem.setOutputFormat("Sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        addCase(problem, 11L, "3 4", "7", true, 0);
        addCase(problem, 12L, "10 20", "30", false, 1);
        addCase(problem, 13L, "", "0", false, 2);
        return problem;
    }

    private void addCase(Problem problem, long id, String input, String expected,
                         boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInputData(input);
        testCase.setExpectedOutput(expected);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        problem.addTestCase(testCase);
    }

    private DomjudgeExecutionService service(FakeClient client) {
        return new DomjudgeExecutionService(client,
                new DomjudgeProblemMirror(client, "demo"), "demo", 10, 5000);
    }

    @Test
    void perRunVerdictsMapToInputsInOrder() {
        FakeClient client = new FakeClient();
        client.runs = List.of(run(0, "AC", 0.01), run(1, "WA", 0.02), run(2, "AC", 0.01));
        Problem problem = problem();

        List<ExecutionResult> results = service(client).judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(JavaExecutor.class));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).providerVerdict()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(results.get(1).providerVerdict()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(results.get(2).providerVerdict()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(client.submits).isEqualTo(1);
    }

    @Test
    void subsetRequestMapsToMatchingCases() {
        FakeClient client = new FakeClient();
        client.runs = List.of(run(0, "AC", 0.01), run(1, "WA", 0.02), run(2, "AC", 0.01));
        Problem problem = problem();

        // Engine asks for the first two only (e.g. samples-first layouts):
        // they must map to full indices 0 and 1, never shifted.
        List<TestCase> requested = new ArrayList<>(problem.getTestCases()).subList(0, 2);
        List<ExecutionResult> results = service(client).judgeTestCases(
                problem, requested, "src", mock(JavaExecutor.class));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).providerVerdict()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(results.get(1).providerVerdict()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
    }

    @Test
    void compilationErrorAppliesToEveryRequestedCase() {
        FakeClient client = new FakeClient();
        client.judgement = "{\"id\": \"1\", \"judgement_type_id\": \"CE\"}";
        client.runs = List.of();
        Problem problem = problem();

        List<ExecutionResult> results = service(client).judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(CppExecutor.class));

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(ExecutionResult::isCompilationError);
    }

    @Test
    void timeAndRuntimeRunsMap() {
        FakeClient client = new FakeClient();
        client.runs = List.of(run(0, "TLE", 5.0), run(1, "RTE", 0.01), run(2, "AC", 0.01));
        Problem problem = problem();

        List<ExecutionResult> results = service(client).judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(CppExecutor.class));

        assertThat(results.get(0).isTimedOut()).isTrue();
        assertThat(results.get(1).exitCode()).isNotEqualTo(0);
        assertThat(results.get(1).isCompilationError()).isFalse();
        assertThat(results.get(2).providerVerdict()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void runCountMismatchFailsLoudInsteadOfMisassigning() {
        FakeClient client = new FakeClient();
        client.runs = List.of(run(0, "AC", 0.01));
        Problem problem = problem();
        DomjudgeExecutionService service = new DomjudgeExecutionService(client,
                new DomjudgeProblemMirror(client, "demo"), "demo", 10, 5000, 50);

        List<ExecutionResult> results = service.judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(CppExecutor.class));

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> !r.hasProviderVerdict());
        assertThat(results.get(0).error()).contains("mismatch");
    }

    @Test
    void slowFlushingRunsAreAwaitedNotFailed() {
        // Live DOMjudge publishes the overall verdict while slow runs (TLE)
        // are still flushing: first only 1/3 runs visible, then all three.
        // The service must wait and map correctly instead of mismatching.
        FakeClient client = new FakeClient();
        client.judgement = "{\"id\": \"1\", \"judgement_type_id\": \"TLE\"}";
        client.runsScript.add(List.of(run(0, "TLE", 2.0)));
        client.runsScript.add(List.of(run(0, "TLE", 2.0), run(1, "TLE", 2.0)));
        client.runs = List.of(run(0, "TLE", 2.0), run(1, "TLE", 2.0), run(2, "TLE", 2.0));
        Problem problem = problem();
        DomjudgeExecutionService service = new DomjudgeExecutionService(client,
                new DomjudgeProblemMirror(client, "demo"), "demo", 10, 5000, 5000);

        List<ExecutionResult> results = service.judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(CppExecutor.class));

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(ExecutionResult::isTimedOut);
    }

    @Test
    void infraFailureDegradesToErrorResults() {
        DomjudgeClient broken = new DomjudgeClient("http://domjudge.invalid", "u", "p") {
            @Override
            public List<ProblemInfo> listProblems(String contest) {
                throw new DomjudgeException("down");
            }
        };
        DomjudgeExecutionService service = new DomjudgeExecutionService(broken,
                new DomjudgeProblemMirror(broken, "demo"), "demo", 10, 5000);
        Problem problem = problem();

        List<ExecutionResult> results = service.judgeTestCases(
                problem, new ArrayList<>(problem.getTestCases()), "src", mock(CppExecutor.class));

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.error().contains("Execution service error"));
    }

    @Test
    void executeBatchExplainsRunCustomLimitation() {
        FakeClient client = new FakeClient();

        List<ExecutionResult> results = service(client).executeBatch(
                "src", List.of("custom input"), mock(CppExecutor.class), 2000, 256);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).error()).contains("Custom input");
    }
}
