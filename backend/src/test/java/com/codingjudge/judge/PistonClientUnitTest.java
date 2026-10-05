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
 * Server-free unit tests for {@link PistonClient}.
 */
class PistonClientUnitTest {

    private PistonClient clientFor(HttpClient http) {
        return new PistonClient("https://emkc.org/api/v2/piston", http, Duration.ofSeconds(2));
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
    void executeReturnsRunOutput() throws Exception {
        String json = "{\"run\":{\"stdout\":\"7\\n\",\"stderr\":\"\",\"code\":0},\"compile\":null}";
        PistonClient client = clientFor(stub(200, json));

        PistonClient.RunResult result =
                client.execute("python", "3.10.0", "main.py", "print(3+4)", "", 2000);

        assertEquals("7\n", result.stdout());
        assertEquals(0, result.code());
        assertNull(result.compile());
    }

    @Test
    void executeParsesCompileFailure() throws Exception {
        String json = "{\"compile\":{\"stdout\":\"\",\"stderr\":\"error: missing semicolon\",\"code\":1},"
                + "\"run\":{\"stdout\":\"\",\"stderr\":\"\",\"code\":0}}";
        PistonClient client = clientFor(stub(200, json));

        PistonClient.RunResult result =
                client.execute("java", "15.0.2", "Main.java", "class Main{}", "", 2000);

        assertNotNull(result.compile());
        assertTrue(result.compile().failed());
        assertEquals("error: missing semicolon", result.compile().stderr());
    }

    @Test
    void executeParsesSignalAsKilled() throws Exception {
        String json = "{\"run\":{\"stdout\":\"\",\"stderr\":\"\",\"code\":137,\"signal\":\"SIGKILL\"},"
                + "\"compile\":null}";
        PistonClient client = clientFor(stub(200, json));

        PistonClient.RunResult result =
                client.execute("c++", "10.2.0", "main.cpp", "int main(){return 0;}", "", 2000);

        assertEquals("SIGKILL", result.signal());
        assertEquals(137, result.code());
    }

    @Test
    void executeThrowsOnNon200() throws Exception {
        PistonClient client = clientFor(stub(503, "Service Unavailable"));

        assertThrows(PistonClient.PistonException.class,
                () -> client.execute("python", "3.10.0", "main.py", "x=1", "", 2000));
    }

    @Test
    void executeThrowsOnMalformedJson() throws Exception {
        PistonClient client = clientFor(stub(200, "not-json"));

        assertThrows(PistonClient.PistonException.class,
                () -> client.execute("python", "3.10.0", "main.py", "x=1", "", 2000));
    }

    @Test
    void executeWrapsIoExceptionAsConnectionException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("refused")).when(http).send(any(), any());
        PistonClient client = clientFor(http);

        assertThrows(PistonClient.PistonConnectionException.class,
                () -> client.execute("python", "3.10.0", "main.py", "x=1", "", 2000));
    }
}
