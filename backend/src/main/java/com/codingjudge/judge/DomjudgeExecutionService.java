package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DOMjudge execution backend for {@link CodeExecutionService} (Phase 9 design).
 *
 * <p>Active only with {@code execution.provider=domjudge}. Unlike Docker and
 * Judge0, DOMjudge judges a WHOLE submission against a mirrored problem, so
 * this provider implements {@link #judgeTestCases} instead of per-input
 * execution. Per-test program output is not exposed by the DOMjudge API, so
 * verdicts come from DOMjudge itself (see {@link ExecutionResult} provider
 * verdicts); our comparator is bypassed on this path by {@link JudgeEngine}.
 *
 * <p>Infra failures degrade to per-input error results (never thrown), same
 * containment contract as the Judge0 provider. Run-custom (arbitrary stdin)
 * has no DOMjudge equivalent: {@link #executeBatch} answers it with a clear
 * error result instead of failing the request.
 */
@Component
@ConditionalOnProperty(name = "execution.provider", havingValue = "domjudge")
public class DomjudgeExecutionService implements CodeExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(DomjudgeExecutionService.class);

    private final DomjudgeClient client;
    private final DomjudgeProblemMirror mirror;
    private final String contest;
    private final long pollIntervalMs;
    private final long maxWaitMs;
    private final long runsWaitMs;

    /** Resolved once, then cached: language ids are stable per DOMjudge host. */
    private final Map<Language, String> languageIds = new ConcurrentHashMap<>();

    @Autowired
    public DomjudgeExecutionService(
            @Value("${domjudge.base-url:}") String baseUrl,
            @Value("${domjudge.contest:}") String contest,
            @Value("${domjudge.user:}") String username,
            @Value("${domjudge.password:}") String password,
            @Value("${domjudge.poll-ms:2000}") long pollIntervalMs,
            @Value("${domjudge.max-wait-ms:300000}") long maxWaitMs) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "execution.provider=domjudge needs domjudge.base-url (DOMJUDGE_BASE_URL)");
        }
        if (contest == null || contest.isBlank()) {
            throw new IllegalArgumentException(
                    "execution.provider=domjudge needs domjudge.contest (DOMJUDGE_CONTEST)");
        }
        if (username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException(
                    "execution.provider=domjudge needs domjudge.user/password (service account)");
        }
        DomjudgeClient created = new DomjudgeClient(baseUrl, username, password);
        this.client = created;
        this.mirror = new DomjudgeProblemMirror(created, contest);
        this.contest = contest;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
        this.runsWaitMs = 120_000;
    }

    /** Test-only seam. */
    DomjudgeExecutionService(DomjudgeClient client, DomjudgeProblemMirror mirror, String contest,
                             long pollIntervalMs, long maxWaitMs) {
        this(client, mirror, contest, pollIntervalMs, maxWaitMs, 120_000);
    }

    /** Test-only seam with an explicit runs-flush wait. */
    DomjudgeExecutionService(DomjudgeClient client, DomjudgeProblemMirror mirror, String contest,
                             long pollIntervalMs, long maxWaitMs, long runsWaitMs) {
        this.client = client;
        this.mirror = mirror;
        this.contest = contest;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
        this.runsWaitMs = runsWaitMs;
    }

    @Override
    public boolean handlesProblemsNatively() {
        return true;
    }

    @Override
    public List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                             LanguageExecutor executor,
                                             long timeoutMs, int memoryLimitMb) {
        // Arbitrary-stdin runs have no DOMjudge equivalent (it judges fixed
        // test data). Fail loud and clear instead of pretending to run.
        List<ExecutionResult> results = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            results.add(ExecutionResult.error(
                    "Custom input runs need the Docker provider; submit the code instead.", -1));
        }
        return results;
    }

    @Override
    public List<ExecutionResult> judgeTestCases(Problem problem, List<TestCase> requested,
                                               String sourceCode, LanguageExecutor executor) {
        List<TestCase> full = orderedFull(problem);
        try {
            String domId = mirror.ensure(problem);
            String languageId = resolveLanguageId(executor);
            String entryPoint = entryPoint(executor, sourceCode);
            String sid = client.submit(contest, languageId, domId,
                    sourceFileName(executor), sourceCode, entryPoint);
            JsonNode judgement = client.awaitJudgement(contest, sid,
                    pollIntervalMs, maxWaitMs);
            return translate(requested, full, judgement, sid);
        } catch (DomjudgeClient.DomjudgeException e) {
            LOG.error("DOMjudge judging failed, marking {} test case(s) as errored: {}",
                    requested.size(), e.getMessage());
            return errorAll(requested.size(),
                    "Execution service error: " + e.getMessage());
        }
    }

    /** Full problem order, same deterministic rule the mirror packages with. */
    private static List<TestCase> orderedFull(Problem problem) {
        return DomjudgeProblemMirror.orderedCases(problem);
    }

    private String sourceFileName(LanguageExecutor executor) {
        try {
            return executor.getSourceFileName();
        } catch (RuntimeException e) {
            return "Main.txt";
        }
    }

    private String entryPoint(LanguageExecutor executor, String sourceCode) {
        if (executor instanceof JavaExecutor) {
            return "Main";
        }
        if (executor instanceof PythonExecutor) {
            String file = sourceFileName(executor);
            int dot = file.lastIndexOf('.');
            return dot > 0 ? file.substring(0, dot) : file;
        }
        return null;
    }

    private String resolveLanguageId(LanguageExecutor executor) {
        Language language = executor instanceof JavaExecutor ? Language.JAVA
                : executor instanceof CppExecutor ? Language.CPP
                : Language.PYTHON;
        return languageIds.computeIfAbsent(language, lang -> {
            String want = switch (lang) {
                case JAVA -> "Java";
                case CPP -> "C++";
                case PYTHON -> "Python";
            };
            List<String> matches = client.listLanguages().stream()
                    .filter(l -> l.name().contains(want))
                    .map(DomjudgeClient.LanguageInfo::id)
                    .sorted()
                    .toList();
            if (matches.isEmpty()) {
                throw new DomjudgeClient.DomjudgeException(
                        "DOMjudge has no language containing '" + want + "'");
            }
            return matches.get(0);
        });
    }

    private List<ExecutionResult> translate(List<TestCase> requested, List<TestCase> full,
                                           JsonNode judgement, String sid) {
        String verdict = judgement.path("judgement_type_id").asText("");
        if ("CE".equals(verdict)) {
            // Compilation poisons the whole submission: no runs exist.
            // The compiler message itself is jury-only in DOMjudge.
            return compilationErrorAll(requested.size());
        }
        // Runs can lag the judgement row: DOMjudge may publish the overall
        // verdict while individual runs (especially slow ones like TLE, ~2s
        // each over dozens of cases) are still flushing — observed live with
        // the verdict present but only 1/35 runs visible. Wait patiently
        // rather than failing on a transient short list.
        List<DomjudgeClient.RunResult> runs = List.of();
        long deadline = System.currentTimeMillis() + runsWaitMs;
        while (true) {
            runs = client.listRuns(contest, judgement.path("id").asText(""));
            if (runs.size() == full.size() && runs.stream()
                    .allMatch(run -> run.judgementTypeId() != null)) {
                break;
            }
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (runs.size() != full.size()) {
            // Never misassign verdicts to the wrong test case: fail loud.
            LOG.error("DOMjudge returned {} runs for {} mirrored cases (submission {})",
                    runs.size(), full.size(), sid);
            return errorAll(requested.size(),
                    "Execution service error: run/testcase count mismatch.");
        }
        // requested ⊆ full, matched by stable id; runs follow full order.
        Map<Long, Integer> fullIndex = new HashMap<>();
        for (int i = 0; i < full.size(); i++) {
            fullIndex.put(full.get(i).getId(), i);
        }
        List<ExecutionResult> results = new ArrayList<>(requested.size());
        for (TestCase wanted : requested) {
            Integer index = fullIndex.get(wanted.getId());
            if (index == null || index >= runs.size()) {
                results.add(ExecutionResult.error(
                        "Execution service error: testcase mapping missing.", -1));
                continue;
            }
            results.add(translateRun(runs.get(index)));
        }
        return results;
    }

    private static ExecutionResult translateRun(DomjudgeClient.RunResult run) {
        long runtimeMs = run.runTimeSeconds() == null
                ? 0 : Math.round(run.runTimeSeconds() * 1000);
        String verdict = run.judgementTypeId();
        if (verdict == null) {
            return ExecutionResult.error("Execution service error: missing run verdict.", -1);
        }
        return switch (verdict) {
            case "AC" -> ExecutionResult.providerVerdict(SubmissionStatus.ACCEPTED, runtimeMs, 0);
            case "WA" -> ExecutionResult.providerVerdict(SubmissionStatus.WRONG_ANSWER, runtimeMs, 0);
            case "TLE", "OLE" -> ExecutionResult.timeout();
            case "RTE" -> ExecutionResult.error("Runtime error", -1);
            case "CE" -> ExecutionResult.compilationError("Compilation failed", -1);
            default -> ExecutionResult.error(
                    "Execution service error: unknown run verdict '" + verdict + "'.", -1);
        };
    }

    private static List<ExecutionResult> compilationErrorAll(int count) {
        List<ExecutionResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(ExecutionResult.compilationError(
                    "Compilation failed (details visible in the DOMjudge jury interface).", -1));
        }
        return results;
    }

    private static List<ExecutionResult> errorAll(int count, String message) {
        List<ExecutionResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(ExecutionResult.error(message, -1));
        }
        return results;
    }
}