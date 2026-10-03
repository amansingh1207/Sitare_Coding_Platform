package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.enums.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Judge0 execution backend for {@link CodeExecutionService} (Phase 2).
 *
 * <p>Active only with {@code execution.provider=judge0}: production hosts
 * without a Docker daemon. Local development keeps using {@link DockerSandbox}
 * ({@code execution.provider=docker}, the default).
 *
 * <p>One project test case costs one Judge0 submission (compile + run):
 * Judge0 has no "compile once, run many" primitive, so batching happens one
 * call at a time here instead of inside one container. Verdict semantics
 * stay in {@link JudgeEngine}: this class only translates Judge0 statuses
 * into {@link ExecutionResult}s, never compares outputs (we never send
 * {@code expected_output}, so hidden answers cannot leak to Judge0 logs).
 */
@Component
@ConditionalOnProperty(name = "execution.provider", havingValue = "judge0")
public class Judge0ExecutionService implements CodeExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(Judge0ExecutionService.class);

    private final Judge0Client client;
    private final long pollIntervalMs;
    private final long maxWaitMs;
    private final boolean perProcessLimits;

    /** Resolved once, then cached: language ids are stable per Judge0 host. */
    private final Map<Language, Integer> languageIds = new ConcurrentHashMap<>();

    @Autowired
    public Judge0ExecutionService(
            @Value("${judge0.base-url:http://localhost:2358}") String baseUrl,
            @Value("${judge0.api.key:}") String apiKey,
            @Value("${judge0.api.host:}") String apiHost,
            @Value("${judge0.poll-ms:1000}") long pollIntervalMs,
            @Value("${judge0.max-wait-ms:120000}") long maxWaitMs,
            @Value("${judge0.per-process-limits:false}") boolean perProcessLimits) {
        this(new Judge0Client(baseUrl,
                        apiKey == null || apiKey.isBlank() ? null : apiKey,
                        apiHost == null || apiHost.isBlank() ? null : apiHost),
                pollIntervalMs, maxWaitMs, perProcessLimits);
    }

    /** Test-only seam: inject a preconfigured client. */
    Judge0ExecutionService(Judge0Client client, long pollIntervalMs,
                          long maxWaitMs, boolean perProcessLimits) {
        this.client = client;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
        this.perProcessLimits = perProcessLimits;
    }

    @Override
    public List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                             LanguageExecutor executor,
                                             long timeoutMs, int memoryLimitMb) {
        double cpuSeconds = Math.max(1.0, timeoutMs / 1000.0);
        double memoryKb = memoryLimitMb * 1024.0;
        List<ExecutionResult> results = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            results.add(executeOne(sourceCode, executor, input, cpuSeconds, memoryKb));
        }
        return results;
    }

    private ExecutionResult executeOne(String sourceCode, LanguageExecutor executor, String input,
                                      double cpuSeconds, double memoryKb) {
        try {
            // Resolved lazily (and cached): a dead Judge0 or an unknown
            // language degrades to error results, never to a thrown exception.
            int languageId = resolveLanguageId(executor);
            String token = client.submit(sourceCode, languageId, input, null,
                    cpuSeconds, memoryKb, perProcessLimits);
            Judge0Client.Judge0Result result = client.waitForTerminal(token, pollIntervalMs, maxWaitMs);
            return translate(result);
        } catch (Judge0Client.Judge0Exception e) {
            // Infrastructure failure (down host, 4xx/5xx, poll timeout): never
            // let it escape and crash the request/worker thread. It surfaces
            // as a failed test case and is logged server-side with the cause.
            // Known limitation: this currently reads as RUNTIME_ERROR, not
            // INTERNAL_ERROR (see JudgeEngine.executionStatus).
            LOG.error("Judge0 execution failed, marking test case as errored: {}", e.getMessage());
            return ExecutionResult.error("Execution service error: " + e.getMessage(), -1);
        }
    }

    private int resolveLanguageId(LanguageExecutor executor) {
        Language language = executor instanceof JavaExecutor ? Language.JAVA
                : executor instanceof CppExecutor ? Language.CPP
                : Language.PYTHON;
        return languageIds.computeIfAbsent(language, lang -> {
            String want = languageNamePrefix(lang);
            return client.listLanguages().stream()
                    .filter(l -> !l.archived() && l.name().startsWith(want))
                    .mapToInt(Judge0Client.LanguageInfo::id)
                    .max()
                    .orElseThrow(() -> new Judge0Client.Judge0Exception(
                            "Judge0 has no language starting with '" + want + "'"));
        });
    }

    static String languageNamePrefix(Language language) {
        return switch (language) {
            case JAVA -> "Java (OpenJDK";
            case CPP -> "C++ (GCC";
            case PYTHON -> "Python (3";
        };
    }

    private static ExecutionResult translate(Judge0Client.Judge0Result result) {
        long runtimeMs = result.timeSeconds() == null
                ? 0 : Math.round(result.timeSeconds() * 1000);
        long memoryKb = result.memoryKilobytes() == null
                ? 0 : Math.round(result.memoryKilobytes());
        int exitCode = result.exitCode() == null ? -1 : result.exitCode();
        return switch (result.statusId()) {
            case Judge0Client.STATUS_ACCEPTED, Judge0Client.STATUS_WRONG_ANSWER ->
                    ExecutionResult.success(
                            result.stdout() == null ? "" : result.stdout(), runtimeMs, memoryKb);
            case Judge0Client.STATUS_TIME_LIMIT_EXCEEDED -> ExecutionResult.timeout();
            case Judge0Client.STATUS_COMPILATION_ERROR -> ExecutionResult.compilationError(
                    firstNonBlank(result.compileOutput(), result.message(), "Compilation failed"),
                    exitCode);
            default -> {
                if (Judge0Client.isRuntimeError(result.statusId())) {
                    yield ExecutionResult.error(
                            firstNonBlank(result.stderr(), result.message(), "Runtime error"),
                            exitCode);
                }
                yield ExecutionResult.error(
                        firstNonBlank(result.message(), result.stderr(),
                                "Judge0 error, status " + result.statusId()),
                        exitCode);
            }
        };
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
