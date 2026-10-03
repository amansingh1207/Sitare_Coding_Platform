package com.codingjudge.judge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Server-free unit tests for {@link Judge0Client}'s HTTP/error mapping.
 * These always run, even when no Judge0 instance is available.
 */
class Judge0ClientUnitTest {

    private Judge0Client clientFor(HttpClient http) {
        return new Judge0Client("http://judge0.invalid", null, http, Duration.ofSeconds(2));
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
    void statusHelpers() {
        assertFalse(Judge0Client.isTerminal(1));
        assertFalse(Judge0Client.isTerminal(2));
        assertTrue(Judge0Client.isTerminal(3));
        assertTrue(Judge0Client.isTerminal(6));
        assertTrue(Judge0Client.isTerminal(13));
        assertTrue(Judge0Client.isRuntimeError(7));
        assertTrue(Judge0Client.isRuntimeError(11));
        assertFalse(Judge0Client.isRuntimeError(3));
        assertFalse(Judge0Client.isRuntimeError(13));
    }

    @Test
    void unauthorizedMapsToAuthException() throws Exception {
        Judge0Client client = clientFor(stub(401, "Unauthorized"));
        assertThrows(Judge0Client.Judge0AuthException.class,
                () -> client.submit("print(1)", 71, null, null, 2.0, 256_000.0));
    }

    @Test
    void invalidLanguageMapsToValidationException() throws Exception {
        Judge0Client client = clientFor(
                stub(422, "{\"language_id\":[\"language with id 999999 doesn't exist\"]}"));
        assertThrows(Judge0Client.Judge0ValidationException.class,
                () -> client.submit("print(1)", 999_999, null, null, 2.0, 256_000.0));
    }

    @Test
    void fullQueueMapsToQueueFullException() throws Exception {
        Judge0Client client = clientFor(stub(503, "{\"error\":\"queue is full\"}"));
        assertThrows(Judge0Client.Judge0QueueFullException.class,
                () -> client.submit("print(1)", 71, null, null, 2.0, 256_000.0));
    }

    @Test
    void successWithoutTokenIsAnError() throws Exception {
        Judge0Client client = clientFor(stub(201, "{}"));
        assertThrows(Judge0Client.Judge0Exception.class,
                () -> client.submit("print(1)", 71, null, null, 2.0, 256_000.0));
    }

    @Test
    void malformedJsonIsAnError() throws Exception {
        Judge0Client client = clientFor(stub(200, "this is not json{{{"));
        assertThrows(Judge0Client.Judge0Exception.class, () -> client.get("some-token"));
    }

    @Test
    void ioFailureMapsToConnectionException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("connection refused")).when(http).send(any(), any());
        Judge0Client client = clientFor(http);
        assertThrows(Judge0Client.Judge0ConnectionException.class,
                () -> client.submit("print(1)", 71, null, null, 2.0, 256_000.0));
    }

    @Test
    void terminalResultIsParsed() throws Exception {
        String body = "{\"token\":\"abc\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                + "\"stdout\":\"hi\\n\",\"stderr\":null,\"compile_output\":null,"
                + "\"message\":null,\"time\":\"0.002\",\"memory\":1024}";
        Judge0Client client = clientFor(stub(200, body));
        Judge0Client.Judge0Result result = client.get("abc");
        assertEquals("abc", result.token());
        assertEquals(3, result.statusId());
        assertEquals("Accepted", result.statusDescription());
        assertEquals("hi\n", result.stdout());
        assertEquals(0.002, result.timeSeconds());
        assertEquals(1024.0, result.memoryKilobytes());
    }
}
