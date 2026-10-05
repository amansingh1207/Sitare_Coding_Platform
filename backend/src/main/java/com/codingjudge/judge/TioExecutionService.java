package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom-input runs through the free TryItOnline API (no key, no signup).
 *
 * <p>Active only with {@code judge.customrun.provider=tio}
 * ({@code JUDGE_CUSTOMRUN_PROVIDER}). Deliberately NOT a {@link
 * CodeExecutionService}: {@link JudgeEngine} keeps selecting the single
 * judging provider (Docker / DOMjudge) untouched, and only the editor's
 * custom-input path is offered to TIO — via an optional injection that
 * stays null everywhere else, including every existing test.
 *
 * <p>Language ids and the request/response protocol were verified live
 * against {@code https://tio.run/cgi-bin/run/api/}. TIO correctly runs
 * {@code public class Main} (unlike Wandbox, which fixes the filename to
 * {@code prog.java}). If TIO is down, {@link TioClient.TioException}
 * propagates and {@link JudgeEngine} falls back to the next provider
 * (which answers with the usual unsupported guidance under DOMjudge).
 */
@Component
@ConditionalOnProperty(name = "judge.customrun.provider", havingValue = "tio")
public class TioExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(TioExecutionService.class);

    static final String JAVA_LANG = "java-jdk";
    static final String CPP_LANG = "cpp-gcc";
    static final List<String> CPP_CFLAGS = List.of("-std=c++17", "-O2");
    static final String PYTHON_LANG = "python3";

    private final TioClient client;

    @Autowired
    public TioExecutionService(
            @Value("${tio.base-url:https://tio.run/cgi-bin/run/api/}") String baseUrl) {
        this(new TioClient(baseUrl));
        LOG.info("TIO custom-run provider ENABLED (baseUrl={})", baseUrl);
    }

    /** Test-only seam: inject a preconfigured client. */
    TioExecutionService(TioClient client) {
        this.client = client;
    }

    /**
     * Run {@code sourceCode} once against {@code stdin} and translate the
     * TIO outcome into an {@link ExecutionResult}.
     *
     * @throws TioClient.TioException when TIO fails or is unreachable
     */
    public ExecutionResult execute(String sourceCode, String stdin,
                                   LanguageExecutor executor) {
        TioClient.RunResult run = client.execute(
                language(executor), sourceCode,
                stdin == null ? "" : stdin, cflags(executor));
        return translate(run);
    }

    private static String language(LanguageExecutor executor) {
        if (executor instanceof JavaExecutor) {
            return JAVA_LANG;
        }
        if (executor instanceof CppExecutor) {
            return CPP_LANG;
        }
        return PYTHON_LANG;
    }

    /** Mirror our Docker flags for C++; interpreted runners need none. */
    private static List<String> cflags(LanguageExecutor executor) {
        if (executor instanceof CppExecutor) {
            return CPP_CFLAGS;
        }
        return List.of();
    }

    private static ExecutionResult translate(TioClient.RunResult run) {
        if (run.exitCode() == 124 || run.exitCode() == 137 || run.exitCode() == 143
                || run.diagnostics().toLowerCase().contains("timed out")) {
            return ExecutionResult.timeout();
        }
        if (run.exitCode() != 0) {
            String combined = !run.diagnostics().isBlank() ? run.diagnostics()
                    : !run.output().isBlank() ? run.output()
                    : "Runtime error (exit code " + run.exitCode() + ")";
            if (isCompileError(combined)) {
                return ExecutionResult.compilationError(combined, run.exitCode());
            }
            return ExecutionResult.error(combined, run.exitCode());
        }
        return ExecutionResult.success(run.output(), 0, 0);
    }

    /**
     * javac/gcc spell it lowercase ({@code ".java:3: error:", "error:"});
     * Python tracebacks spell it {@code NameError} (capital E) and carry a
     * {@code Traceback} header instead.
     */
    private static boolean isCompileError(String text) {
        return text.contains("error:")
                || text.contains("SyntaxError");
    }
}
