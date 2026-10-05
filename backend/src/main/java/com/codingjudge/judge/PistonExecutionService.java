package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Custom-input runs through the free hosted Piston API.
 *
 * <p>Active only with {@code judge.customrun.provider=piston}
 * ({@code JUDGE_CUSTOMRUN_PROVIDER}). Deliberately NOT a {@link
 * CodeExecutionService}: {@link JudgeEngine} keeps selecting the single
 * judging provider (Docker / DOMjudge) untouched, and only the editor's
 * custom-input path is offered to Piston — via an optional injection that
 * stays null everywhere else, including every existing test.
 *
 * <p>Pinned runtime versions were verified live against the public API.
 * If Piston is down, {@link PistonClient.PistonException} propagates and
 * {@link JudgeEngine} falls back to the judging provider (which answers
 * with the usual unsupported guidance under DOMjudge).
 */
@Component
@ConditionalOnProperty(name = "judge.customrun.provider", havingValue = "piston")
public class PistonExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(PistonExecutionService.class);

    static final String CPP_VERSION = "10.2.0";
    static final String JAVA_VERSION = "15.0.2";
    static final String PYTHON_VERSION = "3.10.0";

    private final PistonClient client;

    @Autowired
    public PistonExecutionService(
            @Value("${piston.base-url:https://emkc.org/api/v2/piston}") String baseUrl) {
        this(new PistonClient(baseUrl));
        LOG.info("Piston custom-run provider ENABLED (baseUrl={})", baseUrl);
    }

    /** Test-only seam: inject a preconfigured client. */
    PistonExecutionService(PistonClient client) {
        this.client = client;
    }

    /**
     * Run {@code sourceCode} once against {@code stdin} and translate the
     * Piston outcome into an {@link ExecutionResult}.
     *
     * <p>Signal kills (usually the time or memory limit) read as a timeout:
     * for an interactive custom run the distinction barely matters, and the
     * program's own stderr is preserved in the message either way.
     *
     * @throws PistonClient.PistonException when Piston fails or is unreachable
     */
    public ExecutionResult execute(String sourceCode, String stdin,
                                    LanguageExecutor executor, long timeoutMs) {
        String language = languageId(executor);
        String version = languageVersion(executor);
        String filename;
        try {
            filename = executor.getSourceFileName();
        } catch (RuntimeException e) {
            filename = defaultFilename(executor);
        }
        PistonClient.RunResult run = client.execute(
                language, version, filename, sourceCode,
                stdin == null ? "" : stdin, timeoutMs);
        return translate(run);
    }

    private static String languageId(LanguageExecutor executor) {
        if (executor instanceof JavaExecutor) {
            return "java";
        }
        if (executor instanceof CppExecutor) {
            return "c++";
        }
        return "python";
    }

    private static String languageVersion(LanguageExecutor executor) {
        if (executor instanceof JavaExecutor) {
            return JAVA_VERSION;
        }
        if (executor instanceof CppExecutor) {
            return CPP_VERSION;
        }
        return PYTHON_VERSION;
    }

    private static String defaultFilename(LanguageExecutor executor) {
        if (executor instanceof JavaExecutor) {
            return "Main.java";
        }
        if (executor instanceof CppExecutor) {
            return "main.cpp";
        }
        return "main.py";
    }

    private static ExecutionResult translate(PistonClient.RunResult run) {
        PistonClient.CompileResult compile = run.compile();
        if (compile != null && compile.failed()) {
            String diagnostics = !compile.stderr().isBlank() ? compile.stderr()
                    : !compile.stdout().isBlank() ? compile.stdout()
                    : "Compilation failed";
            return ExecutionResult.compilationError(diagnostics, compile.code());
        }
        if (run.signal() != null && !run.signal().isBlank()) {
            LOG.info("Piston run killed by signal {}", run.signal());
            return ExecutionResult.timeout();
        }
        if (run.code() != 0) {
            String message = !run.stderr().isBlank() ? run.stderr()
                    : !run.stdout().isBlank() ? run.stdout()
                    : "Runtime error (exit code " + run.code() + ")";
            return ExecutionResult.error(message, run.code());
        }
        return ExecutionResult.success(run.stdout(), 0, 0);
    }
}
