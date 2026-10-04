package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.model.enums.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Server-free tests for {@link Judge0ExecutionService}: status mapping,
 * batching, failure containment and language wiring. The live end-to-end
 * path stays in the Phase 1 POC tests.
 */
@ExtendWith(MockitoExtension.class)
class Judge0ExecutionServiceTest {

    private static final String LANGUAGES = "["
            + "{\"id\":70,\"name\":\"Python (2.7.17)\",\"is_archived\":false},"
            + "{\"id\":71,\"name\":\"Python (3.8.1)\",\"is_archived\":false},"
            + "{\"id\":62,\"name\":\"Java (OpenJDK 13.0.1)\",\"is_archived\":false},"
            + "{\"id\":54,\"name\":\"C++ (GCC 9.2.0)\",\"is_archived\":false}]";

    @Mock
    private LanguageExecutor pythonExecutor;

    private static String terminal(int statusId, String description, String extraFields) {
        return "{\"token\":\"t\",\"status\":{\"id\":" + statusId
                + ",\"description\":\"" + description + "\"},"
                + "\"stdout\":null,\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"time\":null,\"memory\":null,\"exit_code\":null"
                + (extraFields == null ? "" : "," + extraFields) + "}";
    }

    /** Stub transport: languages + one POST token + a queue of GET payloads. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpClient stubTransport(Queue<String> gets, AtomicInteger posts) {
        HttpClient http = mock(HttpClient.class);
        Answer<HttpResponse> answer = invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String uri = request.uri().toString();
            if (uri.contains("/languages/all")) {
                return response(200, LANGUAGES);
            }
            if ("POST".equalsIgnoreCase(request.method())) {
                posts.incrementAndGet();
                return response(201, "{\"token\":\"t\"}");
            }
            String body = gets.poll();
            return response(200, body == null ? terminal(1, "In Queue", null) : body);
        };
        try {
            doAnswer(answer).when(http).send(any(), any());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return http;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        doReturnStatus(response, status);
        doReturnBody(response, body);
        return (HttpResponse<String>) response;
    }

    private static void doReturnStatus(HttpResponse<?> response, int status) {
        org.mockito.Mockito.doReturn(status).when(response).statusCode();
    }

    private static void doReturnBody(HttpResponse<?> response, String body) {
        org.mockito.Mockito.doReturn(body).when(response).body();
    }

    private Judge0ExecutionService service(HttpClient http, long maxWaitMs) {
        Judge0Client client = new Judge0Client("http://judge0.invalid", null, http,
                Duration.ofSeconds(2));
        return new Judge0ExecutionService(client, 10, maxWaitMs, false);
    }

    private Queue<String> gets(String... bodies) {
        return new ConcurrentLinkedQueue<>(List.of(bodies));
    }

    @Test
    void acceptedMapsToSuccessWithMetrics() {
        Queue<String> bodies = gets("{\"token\":\"t\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                + "\"stdout\":\"42\\n\",\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"time\":\"0.005\",\"memory\":7000,\"exit_code\":0}");
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 2000);

        List<ExecutionResult> results =
                service.executeBatch("print(40+2)", List.of(""), pythonExecutor, 2000, 256);

        assertThat(results).hasSize(1);
        ExecutionResult result = results.get(0);
        assertThat(result.output()).isEqualTo("42\n");
        assertThat(result.runtimeMs()).isEqualTo(5);
        assertThat(result.memoryUsedKb()).isEqualTo(7000);
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.isCompilationError()).isFalse();
        assertThat(result.isTimedOut()).isFalse();
    }

    @Test
    void compilationErrorMaps() {
        Queue<String> bodies = gets("{\"token\":\"t\",\"status\":{\"id\":6,\"description\":\"Compilation Error\"},"
                + "\"stdout\":null,\"stderr\":null,\"compile_output\":\"Main.java:3: error\","
                + "\"message\":null,\"time\":null,\"memory\":null,\"exit_code\":null}");
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 2000);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.isCompilationError()).isTrue();
        assertThat(result.error()).contains("Main.java:3");
    }

    @Test
    void timeLimitMapsToTimeout() {
        Queue<String> bodies = gets(terminal(5, "Time Limit Exceeded", null));
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 2000);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void runtimeErrorCarriesStderrAndExitCode() {
        Queue<String> bodies = gets("{\"token\":\"t\",\"status\":{\"id\":11,\"description\":\"Runtime Error (NZEC)\"},"
                + "\"stdout\":null,\"stderr\":\"ZeroDivisionError\",\"compile_output\":null,"
                + "\"message\":\"Exited with error status 1\",\"time\":\"0.01\",\"memory\":7000,\"exit_code\":1}");
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 2000);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.isCompilationError()).isFalse();
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.error()).contains("ZeroDivisionError");
    }

    @Test
    void judgeInternalErrorBecomesErrorResult() {
        Queue<String> bodies = gets("{\"token\":\"t\",\"status\":{\"id\":13,\"description\":\"Internal Error\"},"
                + "\"stdout\":null,\"stderr\":null,\"compile_output\":null,"
                + "\"message\":\"box exploded\",\"time\":null,\"memory\":null,\"exit_code\":null}");
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 2000);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.error()).contains("box exploded");
    }

    @Test
    void connectionFailureBecomesErrorResultInsteadOfThrowing() throws Exception {
        HttpClient http = mock(HttpClient.class);
        org.mockito.Mockito.doThrow(new IOException("connection refused"))
                .when(http).send(any(), any());
        Judge0ExecutionService service = service(http, 2000);

        List<ExecutionResult> results =
                service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).error()).contains("Execution service error");
    }

    @Test
    void pollTimeoutBecomesErrorResult() {
        // GET always answers In Queue; the 200 ms budget must win.
        Queue<String> bodies = gets();
        Judge0ExecutionService service = service(stubTransport(bodies, new AtomicInteger()), 200);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.error()).contains("Execution service error");
    }

    @Test
    void unknownLanguageBecomesErrorResult() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> languages = response(200, "[]");
        org.mockito.Mockito.doReturn(languages).when(http).send(any(), any());
        Judge0ExecutionService service = service(http, 2000);

        ExecutionResult result = service.executeBatch("x", List.of(""), pythonExecutor, 2000, 256).get(0);

        assertThat(result.error()).contains("no language");
    }

    @Test
    void batchRunsOneCallPerInput() {
        String accepted = "{\"token\":\"t\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                + "\"stdout\":\"ok\",\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"time\":\"0.001\",\"memory\":1000,\"exit_code\":0}";
        AtomicInteger posts = new AtomicInteger();
        Judge0ExecutionService service =
                service(stubTransport(gets(accepted, accepted), posts), 2000);

        List<ExecutionResult> results =
                service.executeBatch("x", List.of("a", "b"), pythonExecutor, 2000, 256);

        assertThat(results).hasSize(2);
        assertThat(posts.get()).isEqualTo(2);
    }

    @Test
    void javaExecutorResolvesToJavaLanguage() {
        AtomicInteger posts = new AtomicInteger();
        String accepted = "{\"token\":\"t\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                + "\"stdout\":\"ok\",\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"time\":\"0.001\",\"memory\":1000,\"exit_code\":0}";
        Judge0ExecutionService service = service(stubTransport(gets(accepted), posts), 2000);

        service.executeBatch("x", List.of(""), mock(JavaExecutor.class), 2000, 256);

        assertThat(posts.get()).isEqualTo(1);
    }

    @Test
    void cppExecutorResolvesToCppLanguage() {
        AtomicInteger posts = new AtomicInteger();
        String accepted = "{\"token\":\"t\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                + "\"stdout\":\"ok\",\"stderr\":null,\"compile_output\":null,\"message\":null,"
                + "\"time\":\"0.001\",\"memory\":1000,\"exit_code\":0}";
        Judge0ExecutionService service = service(stubTransport(gets(accepted), posts), 2000);

        service.executeBatch("x", List.of(""), mock(CppExecutor.class), 2000, 256);

        assertThat(posts.get()).isEqualTo(1);
    }

    @Test
    void languagePrefixes() {
        assertThat(Judge0ExecutionService.languageNamePrefix(Language.JAVA)).isEqualTo("Java (OpenJDK");
        assertThat(Judge0ExecutionService.languageNamePrefix(Language.CPP)).isEqualTo("C++ (GCC");
        assertThat(Judge0ExecutionService.languageNamePrefix(Language.PYTHON)).isEqualTo("Python (3");
    }
}
