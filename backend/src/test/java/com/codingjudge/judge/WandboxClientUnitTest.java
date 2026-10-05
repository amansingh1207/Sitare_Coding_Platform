package com.codingjudge.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Server-free unit tests for {@link WandboxClient}.
 */
class WandboxClientUnitTest {

    private WandboxClient clientFor(HttpClient http) {
        return new WandboxClient("https://wandbox.org/api", http, Duration.ofSeconds(2));
    }

    @SuppressWarnings("unchecked")
    private HttpClient stub(int status, String body) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(status).when(response).statusCode();
        doReturn(body).when(response).body();
        doReturn(response).when(http).send(any(), any());
        return http;
    }

    @Test
    void compileReturnsProgramOutput() throws Exception {
        String json = "{\"status\":\"0\",\"compiler_output\":\"\",\"compiler_error\":\"\","
                + "\"program_output\":\"7\\n\",\"program_error\":\"\",\"program_exit_code\":\"0\"}";
        WandboxClient client = clientFor(stub(200, json));

        WandboxClient.RunResult result =
                client.compile("cpython-3.13.8", "main.py", "print(3+4)", "", "");

        assertEquals("7\n", result.programOutput());
        assertEquals(0, result.programExitCode());
        assertTrue(result.compilerError().isBlank());
    }

    @Test
    void compileParsesNumericExitCode() throws Exception {
        String json = "{\"status\":\"0\",\"compiler_output\":\"\",\"compiler_error\":\"\","
                + "\"program_output\":\"\",\"program_error\":\"boom\",\"program_exit_code\":1}";
        WandboxClient client = clientFor(stub(200, json));

        WandboxClient.RunResult result =
                client.compile("cpython-3.13.8", "main.py", "raise Exception('boom')", "", "");

        assertEquals(1, result.programExitCode());
        assertEquals("boom", result.programError());
    }

    @Test
    void compileParsesCompilerError() throws Exception {
        String json = "{\"status\":\"1\",\"compiler_output\":\"\",\"compiler_error\":\"prog.cc:1:1: error\","
                + "\"program_output\":\"\",\"program_error\":\"\",\"program_exit_code\":\"\"}";
        WandboxClient client = clientFor(stub(200, json));

        WandboxClient.RunResult result =
                client.compile("gcc-head", "main.cpp", "broken", "", "-std=c++17 -O2");

        assertEquals("prog.cc:1:1: error", result.compilerError());
    }

    @Test
    void compileThrowsOnNon200() throws Exception {
        WandboxClient client = clientFor(stub(429, "Too Many Requests"));

        assertThrows(WandboxClient.WandboxException.class,
                () -> client.compile("gcc-head", "main.cpp", "int main(){}", "", ""));
    }

    @Test
    void compileThrowsOnMalformedJson() throws Exception {
        WandboxClient client = clientFor(stub(200, "not-json"));

        assertThrows(WandboxClient.WandboxException.class,
                () -> client.compile("gcc-head", "main.cpp", "int main(){}", "", ""));
    }

    @Test
    void compileWrapsIoExceptionAsConnectionException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("refused")).when(http).send(any(), any());
        WandboxClient client = clientFor(http);

        assertThrows(WandboxClient.WandboxConnectionException.class,
                () -> client.compile("gcc-head", "main.cpp", "int main(){}", "", ""));
    }

    /**
     * Java's {@code public class Main} only compiles when the file is named
     * {@code Main.java} — the request must carry the filename, not just code.
     */
    @Test
    void compileSendsCodesArrayWithFilename() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        byte[] responseBytes = ("{\"status\":\"0\",\"compiler_output\":\"\",\"compiler_error\":\"\","
                + "\"program_output\":\"hi\",\"program_error\":\"\",\"program_exit_code\":\"0\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/compile.json", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        try {
            WandboxClient client = new WandboxClient(
                    "http://localhost:" + server.getAddress().getPort() + "/api",
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            WandboxClient.RunResult result = client.compile(
                    "openjdk-jdk-21+35", "Main.java", "class Main{}", "", "");

            assertEquals("hi", result.programOutput());
            JsonNode payload = new ObjectMapper().readTree(body.get());
            assertEquals("Main.java", payload.path("codes").path(0).path("file").asText());
            assertEquals("class Main{}", payload.path("codes").path(0).path("code").asText());
            assertFalse(payload.has("code"));
        } finally {
            server.stop(0);
        }
    }
}
