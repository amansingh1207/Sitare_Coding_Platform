package com.codingjudge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Server-free tests for {@link DomjudgeClient}: payload shape, polling,
 * verdict parsing and error mapping.
 */
@ExtendWith(MockitoExtension.class)
class DomjudgeClientTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        org.mockito.Mockito.doReturn(status).when(response).statusCode();
        // Lenient: error paths (401/422/...) often never read the body.
        org.mockito.Mockito.lenient().doReturn(body).when(response).body();
        return (HttpResponse<String>) response;
    }

    @SuppressWarnings("unchecked")
    private static HttpClient stub(Answer<HttpResponse<String>> answer) {
        HttpClient http = mock(HttpClient.class);
        try {
            doAnswer(answer).when(http).send(any(), any());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return http;
    }

    private static DomjudgeClient client(HttpClient http) {
        return new DomjudgeClient("http://domjudge.invalid", "team", "secret",
                http, Duration.ofSeconds(2));
    }

    @Test
    void submitBodyCarriesSourceAsBase64Zip() throws Exception {
        Map<String, Object> body =
                DomjudgeClient.submitBody("cpp", "hello", "h.cpp", "int x = 1;", null);

        assertThat(body.get("language_id")).isEqualTo("cpp");
        assertThat(body.get("problem_id")).isEqualTo("hello");
        assertThat(body).doesNotContainKey("entry_point");
        List<?> files = (List<?>) body.get("files");
        assertThat(files).hasSize(1);
        Map<?, ?> file = (Map<?, ?>) files.get(0);
        assertThat(file.get("mime")).isEqualTo("application/zip");
        byte[] zip = Base64.getDecoder().decode(file.get("data").toString());
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertThat(in.getNextEntry().getName()).isEqualTo("h.cpp");
            String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("int x = 1;");
        }
    }

    @Test
    void submitBodyIncludesEntryPointWhenGiven() {
        Map<String, Object> body =
                DomjudgeClient.submitBody("java", "hello", "Main.java", "x", "Main");

        assertThat(body.get("entry_point")).isEqualTo("Main");
    }

    @Test
    void submitReturnsId() {
        DomjudgeClient client = client(stub(inv -> response(201, "{\"id\": \"42\"}")));

        assertThat(client.submit("demo", "cpp", "hello", "h.cpp", "x", null)).isEqualTo("42");
    }

    @Test
    void submitRejectsUnauthorized() {
        DomjudgeClient client = client(stub(inv -> response(401, "Unauthorized")));

        assertThatThrownBy(() -> client.submit("demo", "cpp", "hello", "h.cpp", "x", null))
                .isInstanceOf(DomjudgeClient.DomjudgeAuthException.class);
    }

    @Test
    void submitRejectsBadRequest() {
        DomjudgeClient client = client(stub(inv -> response(400, "{\"message\":\"No files\"}")));

        assertThatThrownBy(() -> client.submit("demo", "cpp", "hello", "h.cpp", "x", null))
                .isInstanceOf(DomjudgeClient.DomjudgeValidationException.class);
    }

    @Test
    void awaitJudgementPollsUntilVerdict() {
        Queue<String> gets = new ConcurrentLinkedQueue<>(List.of(
                "[]",
                "[{\"id\": \"9\", \"judgement_type_id\": null}]",
                "[{\"id\": \"9\", \"judgement_type_id\": \"AC\"}]"));
        DomjudgeClient client = client(stub(inv -> response(200, gets.poll())));

        var judgement = client.awaitJudgement("demo", "7", 5, 5000);

        assertThat(judgement.path("judgement_type_id").asText()).isEqualTo("AC");
    }

    @Test
    void awaitJudgementTimesOut() {
        DomjudgeClient client = client(stub(inv -> response(200, "[]")));

        assertThatThrownBy(() -> client.awaitJudgement("demo", "7", 5, 50))
                .isInstanceOf(DomjudgeClient.DomjudgeTimeoutException.class);
    }

    @Test
    void listRunsSortsByOrdinal() {
        String body = "["
                + "{\"id\": \"3\", \"ordinal\": 2, \"judgement_type_id\": \"WA\", \"run_time\": 0.01},"
                + "{\"id\": \"2\", \"ordinal\": 0, \"judgement_type_id\": \"AC\", \"run_time\": 0.002}]";
        DomjudgeClient client = client(stub(inv -> response(200, body)));

        List<DomjudgeClient.RunResult> runs = client.listRuns("demo", "9");

        assertThat(runs).extracting(DomjudgeClient.RunResult::ordinal).containsExactly(0, 2);
        assertThat(runs.get(0).judgementTypeId()).isEqualTo("AC");
        assertThat(runs.get(1).runTimeSeconds()).isEqualTo(0.01);
    }

    @Test
    void malformedJsonIsAnError() {
        DomjudgeClient client = client(stub(inv -> response(200, "not json{{{")));

        assertThatThrownBy(() -> client.listLanguages())
                .isInstanceOf(DomjudgeClient.DomjudgeException.class);
    }

    @Test
    void ioFailureIsAConnectionError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        org.mockito.Mockito.doThrow(new IOException("refused")).when(http).send(any(), any());
        DomjudgeClient client = client(http);

        assertThatThrownBy(() -> client.listLanguages())
                .isInstanceOf(DomjudgeClient.DomjudgeConnectionException.class);
    }

    @Test
    void importProblemReturnsId() {
        DomjudgeClient client = client(stub(inv -> response(200, "{\"problem_id\": \"cj-3-ab12\"}")));

        assertThat(client.importProblem("demo", "cj-3-ab12.zip", new byte[]{1, 2, 3}))
                .isEqualTo("cj-3-ab12");
    }

    @Test
    void countsPostsForOneSubmission() {
        AtomicInteger posts = new AtomicInteger();
        DomjudgeClient client = client(stub(inv -> {
            HttpRequest request = inv.getArgument(0);
            if ("POST".equalsIgnoreCase(request.method())) {
                posts.incrementAndGet();
                return response(201, "{\"id\": \"1\"}");
            }
            return response(200, "[]");
        }));

        client.submit("demo", "cpp", "hello", "h.cpp", "x", null);

        assertThat(posts.get()).isEqualTo(1);
    }
}
