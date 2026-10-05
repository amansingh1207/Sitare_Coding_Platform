package com.codingjudge.judge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Server-free unit tests for {@link TioClient} (a loopback
 * {@link HttpServer} stands in for tio.run).
 */
class TioClientUnitTest {

    private static final String TOKEN = "0123456789abcdef";

    private static String tioResponse(String stdout, String diagnostics, int exitCode) {
        return TOKEN + stdout + TOKEN + diagnostics
                + "\nReal time: 0.050 s\nUser time: 0.030 s\nSys. time: 0.010 s\n"
                + "CPU share: 80.00 %\nExit code: " + exitCode + TOKEN;
    }

    private WandboxStyleServer serverFor(byte[] responseBytes) throws IOException {
        return new WandboxStyleServer(responseBytes, 200);
    }

    /** Minimal loopback server capturing the request body. */
    private static class WandboxStyleServer implements AutoCloseable {
        final HttpServer server;
        final AtomicReference<byte[]> body = new AtomicReference<>();

        WandboxStyleServer(byte[] responseBytes, int status) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api", exchange -> {
                body.set(exchange.getRequestBody().readAllBytes());
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(status, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
            server.start();
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort() + "/api";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String inflate(byte[] deflated) throws Exception {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(deflated);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && inflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            inflater.end();
        }
    }

    @Test
    void executeSendsDeflatedTioProtocolAndParsesSuccess() throws Exception {
        byte[] response = tioResponse("42\n", "", 0).getBytes(StandardCharsets.UTF_8);
        try (WandboxStyleServer server = serverFor(response)) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            TioClient.RunResult result =
                    client.execute("python3", "print(41+1)", "41", java.util.List.of());

            assertEquals("42\n", result.output());
            assertEquals(0, result.exitCode());
            assertTrue(result.diagnostics().isBlank());

            String request = inflate(server.body.get());
            assertTrue(request.contains("Vlang\0" + "1\0python3\0"));
            assertTrue(request.contains("F.code.tio"));
            assertTrue(request.contains("print(41+1)"));
            assertTrue(request.contains("F.input.tio"));
            assertTrue(request.endsWith("R"));
        }
    }

    @Test
    void executeSendsCflagsOnePerItem() throws Exception {
        byte[] response = tioResponse("7", "", 0).getBytes(StandardCharsets.UTF_8);
        try (WandboxStyleServer server = serverFor(response)) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            client.execute("cpp-gcc", "int main(){}", "",
                    java.util.List.of("-std=c++17", "-O2"));

            String request = inflate(server.body.get());
            assertTrue(request.contains("VTIO_CFLAGS\0" + "2\0-std=c++17\0-O2\0"));
        }
    }

    @Test
    void executeParsesCompileError() throws Exception {
        String diagnostics = ".code.tio.cpp:1:19: error: expected ';'\n";
        byte[] response = tioResponse("", diagnostics, 1).getBytes(StandardCharsets.UTF_8);
        try (WandboxStyleServer server = serverFor(response)) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            TioClient.RunResult result =
                    client.execute("cpp-gcc", "bad", "", java.util.List.of());

            assertEquals(1, result.exitCode());
            assertTrue(result.diagnostics().contains("error: expected"));
            assertFalse(result.diagnostics().contains("Exit code"));
        }
    }

    @Test
    void executeHandlesGzippedResponse() throws Exception {
        byte[] raw = tioResponse("hi\n", "", 0).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
            gzip.write(raw);
        }
        try (WandboxStyleServer server = serverFor(gzipped.toByteArray())) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            TioClient.RunResult result =
                    client.execute("python3", "print('hi')", "", java.util.List.of());

            assertEquals("hi\n", result.output());
            assertEquals(0, result.exitCode());
        }
    }

    @Test
    void executeThrowsOnNon200() throws Exception {
        try (WandboxStyleServer server = new WandboxStyleServer(
                "busy".getBytes(StandardCharsets.UTF_8), 429)) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            assertThrows(TioClient.TioException.class,
                    () -> client.execute("python3", "x=1", "", java.util.List.of()));
        }
    }

    @Test
    void executeThrowsOnShortResponse() throws Exception {
        try (WandboxStyleServer server = serverFor("oops".getBytes(StandardCharsets.UTF_8))) {
            TioClient client = new TioClient(server.baseUrl(),
                    HttpClient.newHttpClient(), Duration.ofSeconds(5));

            assertThrows(TioClient.TioException.class,
                    () -> client.execute("python3", "x=1", "", java.util.List.of()));
        }
    }

    @Test
    void executeWrapsIoExceptionAsConnectionException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("refused")).when(http).send(any(), any());
        TioClient client = new TioClient("http://tio.invalid", http, Duration.ofSeconds(2));

        assertThrows(TioClient.TioConnectionException.class,
                () -> client.execute("python3", "x=1", "", java.util.List.of()));
    }

    @Test
    void parseHandlesUnframedErrorText() {
        TioClient.RunResult result = TioClient.parse(
                "0123456789abcdefThe language 'nope' could not be found on the server.");

        assertEquals(-1, result.exitCode());
        assertTrue(result.diagnostics().contains("could not be found"));
    }
}
