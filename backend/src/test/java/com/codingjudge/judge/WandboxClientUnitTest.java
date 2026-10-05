package com.codingjudge.judge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

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
                client.compile("cpython-3.13.8", "print(3+4)", "", "");

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
                client.compile("cpython-3.13.8", "raise Exception('boom')", "", "");

        assertEquals(1, result.programExitCode());
        assertEquals("boom", result.programError());
    }

    @Test
    void compileParsesCompilerError() throws Exception {
        String json = "{\"status\":\"1\",\"compiler_output\":\"\",\"compiler_error\":\"prog.cc:1:1: error\","
                + "\"program_output\":\"\",\"program_error\":\"\",\"program_exit_code\":\"\"}";
        WandboxClient client = clientFor(stub(200, json));

        WandboxClient.RunResult result =
                client.compile("gcc-head", "broken", "", "-std=c++17 -O2");

        assertEquals("prog.cc:1:1: error", result.compilerError());
    }

    @Test
    void compileThrowsOnNon200() throws Exception {
        WandboxClient client = clientFor(stub(429, "Too Many Requests"));

        assertThrows(WandboxClient.WandboxException.class,
                () -> client.compile("gcc-head", "int main(){}", "", ""));
    }

    @Test
    void compileThrowsOnMalformedJson() throws Exception {
        WandboxClient client = clientFor(stub(200, "not-json"));

        assertThrows(WandboxClient.WandboxException.class,
                () -> client.compile("gcc-head", "int main(){}", "", ""));
    }

    @Test
    void compileWrapsIoExceptionAsConnectionException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("refused")).when(http).send(any(), any());
        WandboxClient client = clientFor(http);

        assertThrows(WandboxClient.WandboxConnectionException.class,
                () -> client.compile("gcc-head", "int main(){}", "", ""));
    }
}
