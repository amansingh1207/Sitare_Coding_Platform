package com.codingjudge.judge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * Thin client for the free TryItOnline execution API (https://tio.run).
 *
 * <p>Used ONLY for single custom-input runs from the editor: TIO executes
 * arbitrary stdin and returns the real stdout, which DOMjudge can never do.
 * No key, no signup, no cost. Uses only the JDK HTTP client plus
 * {@link Deflater}: no new dependencies.
 *
 * <p>Protocol (reverse-engineered from the site's own traffic and the
 * reference client at github.com/null8626/tio.js, verified live):
 * <pre>
 * POST /cgi-bin/run/api/  (raw-DEFLATE body, {@code application/octet-stream})
 *   Vlang\01\0java-jdk\0F.code.tio\0&lt;len&gt;\0&lt;code&gt;\0F.input.tio\0&lt;len&gt;\0&lt;stdin&gt;\0R
 *   -&gt; 200 &lt;token(16)&gt;&lt;stdout&gt;&lt;token&gt;&lt;diagnostics+footer&gt;&lt;token&gt;
 *   (raw text, or gzip when the server chooses to compress)
 * </pre>
 * File lengths are UTF-8 byte counts; {@code V} lengths are item counts.
 * The footer ({@code Real time: ... Exit code: N}) is parsed for the exit code
 * and stripped from the diagnostics shown to students.
 */
public class TioClient {

    private static final Pattern FOOTER = Pattern.compile(
            "[\\s\\S]*Real time: [\\d.]+ s\\nUser time: [\\d.]+ s\\nSys\\. time: [\\d.]+ s\\n"
                    + "CPU share: [\\d.]+ %\\nExit code: (\\d+)\\s*$");
    private static final Pattern FOOTER_STRIP = Pattern.compile(
            "\\n?Real time: [\\d.]+ s\\nUser time: [\\d.]+ s\\nSys\\. time: [\\d.]+ s\\n"
                    + "CPU share: [\\d.]+ %\\nExit code: \\d+\\s*$");

    private final String baseUrl;
    private final HttpClient http;
    private final Duration requestTimeout;

    public TioClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(90));
    }

    /** Test-only seam: inject a stubbed {@link HttpClient}. */
    TioClient(String baseUrl, HttpClient http, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.requestTimeout = requestTimeout;
    }

    /** One synchronous TIO execution: program stdout, stderr/diagnostics, exit code. */
    public record RunResult(String output, String diagnostics, int exitCode) {
    }

    /** Base failure: malformed responses and non-200 statuses. */
    public static class TioException extends RuntimeException {
        TioException(String message) {
            super(message);
        }

        TioException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The API host itself is unreachable (DNS, refused, timeout). */
    public static class TioConnectionException extends TioException {
        TioConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Run {@code code} in {@code languageId} (a TIO language id such as
     * {@code java-jdk}, {@code cpp-gcc} or {@code python3}) with {@code stdin}.
     */
    public RunResult execute(String languageId, String code, String stdin,
                             List<String> cflags) {
        try {
            byte[] body = deflate(buildRequest(languageId, code,
                    stdin == null ? "" : stdin,
                    cflags == null ? List.of() : cflags));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response =
                    http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new TioException("TIO execute failed with HTTP "
                        + response.statusCode());
            }
            return parse(decode(response.body()));
        } catch (IOException e) {
            throw new TioConnectionException(
                    "Cannot reach TIO at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TioException("TIO request interrupted", e);
        }
    }

    static byte[] buildRequest(String languageId, String code, String stdin,
                               List<String> cflags) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        variable(out, "args", List.of());
        variable(out, "lang", List.of(languageId));
        if (!cflags.isEmpty()) {
            variable(out, "TIO_CFLAGS", cflags);
        }
        variable(out, "TIO_OPTIONS", List.of());
        file(out, ".code.tio", code);
        file(out, ".input.tio", stdin);
        out.write('R');
        return out.toByteArray();
    }

    private static void variable(ByteArrayOutputStream out, String name, List<String> items) {
        write(out, "V" + name + "\0" + items.size() + "\0");
        for (String item : items) {
            write(out, item + "\0");
        }
    }

    private static void file(ByteArrayOutputStream out, String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        write(out, "F" + name + "\0" + bytes.length + "\0");
        out.write(bytes, 0, bytes.length);
        out.write(0);
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] deflate(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 16);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static String decode(byte[] body) throws IOException {
        byte[] raw = body;
        if (body.length >= 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                gzip.transferTo(out);
                raw = out.toByteArray();
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    static RunResult parse(String text) {
        if (text.length() < 16) {
            throw new TioException("TIO response too short to contain a token");
        }
        String token = text.substring(0, 16);
        String[] parts = text.substring(16).split(Pattern.quote(token), -1);
        String output = parts.length > 1 ? parts[0] : "";
        String diagnostics = parts.length > 2 ? parts[1] : "";
        Matcher footer = FOOTER.matcher(diagnostics);
        int exitCode = -1;
        if (footer.matches()) {
            try {
                exitCode = Integer.parseInt(footer.group(1));
            } catch (NumberFormatException e) {
                exitCode = -1;
            }
            diagnostics = FOOTER_STRIP.matcher(diagnostics).replaceFirst("").strip();
        } else if (parts.length <= 2) {
            // No token framing at all (e.g. "The language 'x' could not be found"):
            // surface the raw text so logs show the real reason.
            diagnostics = text.substring(16).strip();
        }
        return new RunResult(output, diagnostics, exitCode);
    }
}
