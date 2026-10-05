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
 * Custom-input runs through the free Wandbox API (no key, no signup).
 *
 * <p>Active only with {@code judge.customrun.provider=wandbox}
 * ({@code JUDGE_CUSTOMRUN_PROVIDER}). Deliberately NOT a {@link
 * CodeExecutionService}: {@link JudgeEngine} keeps selecting the single
 * judging provider (Docker / DOMjudge) untouched, and only the editor's
 * custom-input path is offered to Wandbox — via an optional injection that
 * stays null everywhere else, including every existing test.
 *
 * <p>Pinned compiler names were verified live against
 * {@code GET https://wandbox.org/api/list.json}. If Wandbox is down or rate
 * limits us, {@link WandboxClient.WandboxException} propagates and {@link
 * JudgeEngine} falls back to the judging provider (which answers with the
 * usual unsupported guidance under DOMjudge).
 */
@Component
@ConditionalOnProperty(name = "judge.customrun.provider", havingValue = "wandbox")
public class WandboxExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(WandboxExecutionService.class);

    static final String CPP_COMPILER = "gcc-head";
    static final String CPP_RAW_OPTIONS = "-std=c++17 -O2";
    static final String JAVA_COMPILER = "openjdk-jdk-21+35";
    static final String PYTHON_COMPILER = "cpython-3.13.8";

    private final WandboxClient client;

    @Autowired
    public WandboxExecutionService(
            @Value("${wandbox.base-url:https://wandbox.org/api}") String baseUrl) {
        this(new WandboxClient(baseUrl));
        LOG.info("Wandbox custom-run provider ENABLED (baseUrl={})", baseUrl);
    }

    /** Test-only seam: inject a preconfigured client. */
    WandboxExecutionService(WandboxClient client) {
        this.client = client;
    }

    /**
     * Run {@code sourceCode} once against {@code stdin} and translate the
     * Wandbox outcome into an {@link ExecutionResult}.
     *
     * @throws WandboxClient.WandboxException when Wandbox fails or is unreachable
     */
    public ExecutionResult execute(String sourceCode, String stdin,
                                   LanguageExecutor executor) {
        String filename;
        try {
            filename = executor.getSourceFileName();
        } catch (RuntimeException e) {
            filename = null;
        }
        if (filename == null || filename.isBlank()) {
            filename = defaultFilename(executor);
        }
        WandboxClient.RunResult run = client.compile(
                compiler(executor), filename, sourceCode,
                stdin == null ? "" : stdin, rawOptions(executor));
        return translate(run);
    }

    private static String compiler(LanguageExecutor executor) {
        if (executor instanceof JavaExecutor) {
            return JAVA_COMPILER;
        }
        if (executor instanceof CppExecutor) {
            return CPP_COMPILER;
        }
        return PYTHON_COMPILER;
    }

    /** Mirror our Docker flags where the compiler accepts raw options. */
    private static String rawOptions(LanguageExecutor executor) {
        if (executor instanceof CppExecutor) {
            return CPP_RAW_OPTIONS;
        }
        return "";
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

    private static ExecutionResult translate(WandboxClient.RunResult run) {
        if (!run.compilerError().isBlank()) {
            return ExecutionResult.compilationError(run.compilerError(), 1);
        }
        if (run.programExitCode() != 0) {
            String message = !run.programError().isBlank() ? run.programError()
                    : !run.programOutput().isBlank() ? run.programOutput()
                    : "Runtime error (exit code " + run.programExitCode() + ")";
            return ExecutionResult.error(message, run.programExitCode());
        }
        return ExecutionResult.success(run.programOutput(), 0, 0);
    }
}
